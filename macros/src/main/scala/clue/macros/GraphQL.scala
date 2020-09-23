package clue.macros

import cats.syntax.all._
import scala.annotation.StaticAnnotation
import scala.reflect.macros.blackbox
import scala.annotation.tailrec
import scala.reflect.io.File
import java.io.{ File => JFile }
import edu.gemini.grackle._
import edu.gemini.grackle.{ Type => GType }
import edu.gemini.grackle.{ NoType => GNoType }
import edu.gemini.grackle.{ TypeRef => GTypeRef }
import scala.annotation.Annotation
import io.circe.parser.decode
import io.circe.ParsingFailure
import scala.util.Success
import scala.util.Failure
import edu.gemini.grackle.UntypedOperation.UntypedQuery
import edu.gemini.grackle.UntypedOperation.UntypedMutation
import edu.gemini.grackle.UntypedOperation.UntypedSubscription
import cats.data.Ior
import shapeless.PolyDefns.Case

class GraphQL(
  schema:   String,
  mappings: Map[String, String] = Map.empty,
  eq:       Boolean = false,
  show:     Boolean = false,
  lenses:   Boolean = false,
  reuse:    Boolean = false,
  debug:    Boolean = false
) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro GraphQLImpl.expand
}

// Parameter order and names must match exactly between this class and annotation class.
case class GraphQLOptionalParams(
  val mappings: Some[Map[String, String]] = Some(Map.empty),
  val eq:       Option[Boolean] = None,
  val show:     Option[Boolean] = None,
  val lenses:   Option[Boolean] = None,
  val reuse:    Option[Boolean] = None,
  val debug:    Some[Boolean] = Some(false)
) {
  def resolve(settings: MacroSettings): GraphQLParams =
    GraphQLParams(
      mappings.get,
      eq.getOrElse(settings.catsEq),
      show.getOrElse(settings.catsShow),
      lenses.getOrElse(settings.monocleLenses),
      reuse.getOrElse(settings.scalajsReactReusability),
      debug.get
    )
}

case class GraphQLParams(
  mappings: Map[String, String],
  eq:       Boolean,
  show:     Boolean,
  lenses:   Boolean,
  reuse:    Boolean,
  debug:    Boolean
)

private[clue] final class GraphQLImpl(val c: blackbox.Context) extends GrackleMacro {
  import c.universe._

  /**
   * Extract the `document` contents from the `GraphQLQuery` the marcro was applied to.
   */
  @tailrec
  private[this] def documentDef(tree: List[c.Tree]): Option[String] =
    tree match {
      case Nil                                          => none
      case q"$mods val document: $tpt = $document" :: _ =>
        scala.util.Try(c.eval(c.Expr[String](c.untypecheck(document.duplicate)))).toOption
      case _ :: tail                                    => documentDef(tail)
    }

  /**
   *  Holds the aggregated [[CaseClass]]es and their [[ClassParam]]s as we recurse the query AST.
   *
   * `parAccum` accumulates parameters until we have a whole case class definition.
   */
  private[this] case class ClassAccumulator(
    classes:  List[CaseClass] = List.empty,
    parAccum: List[ClassParam] = List.empty
  )

  /**
   * Recurse the query AST and collect the necessary [[CaseClass]]es to hold its results.
   */
  private[this] def resolveData(
    algebra:  Query,
    rootType: GType
  ): List[CaseClass] = {
    import Query._

    def go(
      currentAlgebra: Query,
      currentType:    GType,
      nameOverride:   Option[String] = none
    ): ClassAccumulator =
      currentAlgebra match {
        case Select(name, args, child) =>
          val nextType   = currentType.field(name)
          val newClasses =
            nextType.underlyingObject match {
              case GNoType  => Nil
              case baseType =>
                val next          = go(child, baseType)
                val caseClassName = baseType.asNamed
                  .map(_.name.capitalize)
                  .getOrElse(abort(s"Unexpected unnamed underlying type for [$baseType]"))
                next.classes :+ CaseClass(caseClassName, next.parAccum)
            }
          ClassAccumulator(
            classes = newClasses,
            parAccum = List(ClassParam(nameOverride.getOrElse(name), nextType.dealias))
          )
        case Rename(name, child)       =>
          go(child, currentType, name.some)
        case Group(selections)         =>
          selections
            .map(q => go(q, currentType))
            .foldLeft(ClassAccumulator())((r1, r2) =>
              ClassAccumulator(r1.classes ++ r2.classes, r1.parAccum ++ r2.parAccum)
            )
        case Empty                     => ClassAccumulator()
        case _                         =>
          log(s"Unhandled Algebra: [$algebra]")
          ClassAccumulator()
      }

    val algebraTypes = go(algebra, rootType.underlyingObject)

    algebraTypes.classes :+ CaseClass("Data", algebraTypes.parAccum)
  }

  // This might be useful in Grackle?
  private[this] def underlyingInputObject(tpe: GType): GType = {
    log(s"Searching for [$tpe] - [${tpe.getClass()}]")
    tpe match {
      case NullableType(tpe)  => underlyingInputObject(tpe)
      case ListType(tpe)      => underlyingInputObject(tpe)
      case _: GTypeRef        => underlyingInputObject(tpe.dealias)
      case o: InputObjectType => o
      case _                  => GNoType
    }
  }

  /**
   * Resolve the types needed to define another type.
   */
  private[this] def typeDependencies(
    tpe:     GType,
    visited: List[CaseClass] = List.empty
  ): List[CaseClass] = {
    log(
      s"Type dep for [$tpe] - class [${tpe.getClass}] - under: [${underlyingInputObject(tpe)}] - under class [${underlyingInputObject(tpe).getClass()}]"
    )
    underlyingInputObject(tpe) match {
      case InputObjectType(name, _, inputFields) if !visited.exists(_.name == name.capitalize) =>
        val deps = inputFields.foldLeft(List.empty[CaseClass])((visitedNow, field) =>
          visitedNow ++ typeDependencies(field.tpe, visitedNow ++ visited)
        )

        deps :+ CaseClass(name.capitalize,
                          inputFields.map(field => ClassParam(field.name, field.tpe))
        )
      case _                                                                                   => List.empty
    }
  }

  /**
   * Resolve the types of the operation's variable arguments.
   */
  private[this] def resolveVariables(
    schema: Schema,
    vars:   List[Query.UntypedVarDef]
  ): List[CaseClass] = {
    val inputs = compileVarDefs(schema, vars)

    // Actually define a sum type here.
    val enums = schema.types.collect { case EnumType(name, _, _) =>
      CaseClass(name.capitalize, List.empty)
    }

    val inputClasses = schema.types
      .collect { case InputObjectType(name, _, fields) =>
        CaseClass(name.capitalize, fields.map(iv => ClassParam(iv.name, iv.tpe)))
      }

    if (inputs.isLeft)
      abort(s"Error resolving operation input variables types [$vars]: [${inputs.left}]]")
    if (inputs.isBoth)
      log(s"Warning resolving operation input variables types [$vars]: [${inputs.left}]]")
    val inputValues = inputs.right.get
    (enums ++ inputClasses) :+
      CaseClass("Variables", inputValues.map(iv => ClassParam(iv.name, iv.tpe)))
  }

  /**
   * Parse the schema file.
   */
  private[this] def retrieveSchema(resourceDirs: List[JFile], schemaName: String): Schema = {
    val fileName = s"$schemaName.graphql"
    resourceDirs.view.map(dir => new JFile(dir, fileName)).find(_.exists) match {
      case None             => abort(s"No schema [$fileName] found in paths [${resourceDirs.mkString(", ")}]")
      case Some(schemaFile) =>
        val schemaString = new File(schemaFile).slurp()
        val schema       = Schema(schemaString)
        if (schema.isLeft)
          abort(
            s"Could not parse schema at [${schemaFile.getAbsolutePath}]: ${schema.left.get.toChain.map(_.toString).toList.mkString("\n")}"
          )
        if (schema.isBoth)
          log(
            s"Warning when parsing schema [${schemaFile.getAbsolutePath}]: ${schema.left.get.toChain.map(_.toString).toList.mkString("\n")}"
          )
        schema.right.get
    }
  }

  /**
   * Parse the schema meta file, if any.
   */
  private[this] def retrieveSchemaMeta(
    resourceDirs: List[JFile],
    schemaName:   String
  ): SchemaMeta = {
    val fileName = s"$schemaName.meta.json"
    resourceDirs.view.map(dir => new JFile(dir, fileName)).find(_.exists) match {
      case None           => SchemaMeta.Default
      case Some(metaFile) =>
        val json = new File(metaFile).slurp()
        SchemaMeta.fromJson(json) match {
          case Success(schemaMeta) => SchemaMeta.Default.combine(schemaMeta)
          case Failure(failure)    =>
            abort(s"Could not parse schema metadata at [${metaFile.getAbsolutePath}]:\n $failure")
        }
    }
  }

  // We cannot fully resolve types since we cannot evaluate in the current annotated context.
  // Therefore, the schema name is always treated as unqualified, and therefore must be unique
  // across the whole compilation unit.
  // See: https://stackoverflow.com/questions/19379436/cant-access-parents-members-while-dealing-with-macro-annotations
  private[this] def schemaType(list: List[c.Tree]): Option[c.Tree] =
    list.collect {
      case tq"GraphQLOperation[$schema]"      => schema
      case tq"clue.GraphQLOperation[$schema]" => schema
    }.headOption

  /**
   * Actual macro application, generating case classes to hold the query results and its variables.
   */
  final def expand(annottees: Tree*): Tree =
    annottees match {
      case List(
            q"$objMods object $objName extends { ..$objEarlyDefs } with ..$objParents { $objSelf => ..$objDefs }"
          ) =>
        schemaType(objEarlyDefs).orElse(schemaType(objParents)) match {
          case None =>
            abort(
              "Invalid annotation target: must be an object extending GraphQLOperation[Schema]"
            )

          case Some(schemaType) =>
            documentDef(objDefs) match {
              case None =>
                abort(
                  "The GraphQLOperation must define a 'val document: String' that can be evaluated at compile time."
                )

              case Some(document) =>
                // Get macro settings passed thru -Xmacro-settings.
                val settings = MacroSettings.fromCtxSettings(c.settings)

                // Get annotation parameters.
                // TODO Can we generalize this and move to Macro trait?
                val optionalParams = c.prefix.tree match {
                  case q"new ${macroName}(..$params)" =>
                    val Ident(TypeName(macroClassName)) = macroName

                    val paramsClassName =
                      parseType(s"clue.macros.${macroClassName}OptionalParams")

                    // Convert parameters to Some(...).
                    val optionalParams = params.map {
                      case value @ Literal(Constant(_)) =>
                        Apply(Ident(TermName("Some")), List(value))
                      case NamedArg(name, value)        =>
                        NamedArg(name, Apply(Ident(TermName("Some")), List(value)))
                    }

                    c.eval(
                      c.Expr[GraphQLOptionalParams](
                        q"new $paramsClassName(..$optionalParams)"
                      )
                    )
                  case q"new ${macroName}"            => GraphQLOptionalParams()
                }

                val params = optionalParams.resolve(settings)

                // Parse schema and metadata.
                val schemaTypeName = unqualifiedType(schemaType) match {
                  case None       =>
                    abort(s"Could not extract unqualified type from schema type [$schemaType]")
                  case Some(name) => name
                }
                val schema         = retrieveSchema(settings.schemaDirs, schemaTypeName)
                val schemaMeta     =
                  retrieveSchemaMeta(settings.schemaDirs, schemaTypeName).addMappings(
                    params.mappings
                  )

                // Check if a Data class and module are already defined.
                val hasDataClass  = objDefs.exists {
                  case q"$mods class Data $ctorMods(...$paramss) extends { ..$earlyDefs } with ..$parents { $self => ..$stats }" =>
                    true
                  case _                                                                                                         => false
                }
                val hasDataModule = objDefs.exists {
                  case q"$mods object Data extends { ..$earlyDefs } with ..$parents { $self => ..$stats }" =>
                    true
                  case _                                                                                   => false
                }

                // Check if a Variables class and module are already defined.
                val varParams          = objDefs.collect {
                  case q"$mods class Variables $ctorMods(...$paramss) extends { ..$earlyDefs } with ..$parents { $self => ..$stats }" =>
                    paramss
                }.headOption
                val hasVariablesClass  = varParams.isDefined
                val hasVariablesModule = objDefs.exists {
                  case q"$mods object Variables extends { ..$earlyDefs } with ..$parents { $self => ..$stats }" =>
                    true
                  case _                                                                                        => false
                }

                // Parse the operation.
                val queryResult = QueryParser.parseText(document)
                if (queryResult.isLeft)
                  abort(
                    s"Could not parse document: ${queryResult.left.get.toChain.map(_.toString).toList.mkString("\n")}"
                  )
                if (queryResult.isBoth)
                  log(
                    s"Warning parsing document: ${queryResult.left.get.toChain.map(_.toString).toList.mkString("\n")}"
                  )
                val operation   = queryResult.toOption.get

                // Build AST to define case classes to hold Data.
                val dataDefs: List[Tree] =
                  if (!hasDataClass) {
                    // Resolve types needed for the query result and its variables.
                    val dataClasses = resolveData(operation.query, schema.queryType)
                    dataClasses
                      .map(
                        _.toTree(schemaMeta.mappings,
                                 params.eq,
                                 params.show,
                                 params.lenses,
                                 params.reuse,
                                 decoder = true
                        )
                      )
                      .flatten
                  } else if (!hasDataModule)
                    List(moduleDef("Data", params.eq, params.show, params.reuse, decoder = true))
                  else
                    List.empty
                val dataDecoderDef       =
                  if (!hasDataModule)
                    q"override val dataDecoder: io.circe.Decoder[Data]     = Data.jsonDecoderData"
                  else EmptyTree

                // Build AST to define case classe to hold Variables.
                val variablesClasses    = resolveVariables(schema, operation.variables)
                val variablesDefs       =
                  if (!hasVariablesClass)
                    variablesClasses
                      .map(
                        _.toTree(schemaMeta.mappings,
                                 params.eq,
                                 params.show,
                                 params.lenses,
                                 params.reuse,
                                 encoder = true
                        )
                      )
                      .flatten
                  else if (!hasVariablesModule)
                    List(
                      moduleDef("Variables", params.eq, params.show, reuse = false, encoder = true)
                    )
                  else
                    List.empty
                val variablesEncoderDef =
                  if (!hasVariablesModule)
                    q"override val varEncoder: io.circe.Encoder[Variables] = Variables.jsonEncoderVariables"
                  else EmptyTree

                // Build convenience method.
                val variablesParams      =
                  varParams.getOrElse(
                    List(variablesClasses.last.params.map(_.toTree(schemaMeta.mappings)))
                  )
                val variablesNames       = variablesParams
                  .map(_.map { case q"$mods val $name: $tpt = $rhs" => name })
                val convenienceMethodDef =
                  operation match {
                    case _: UntypedQuery        =>
                      q"""
                        def query[F[_]](...$variablesParams)(implicit client: _root_.clue.GraphQLClient[F, $schemaType]) =
                          client.request(this)(Variables(...$variablesNames))
                      """
                    case _: UntypedMutation     =>
                      q"""
                        def mutate[F[_]](...$variablesParams)(implicit client: _root_.clue.GraphQLClient[F, $schemaType]) =
                          client.request(this)(Variables(...$variablesNames))
                      """
                    case _: UntypedSubscription =>
                      q"""
                        def subscribe[F[_]](...$variablesParams)(implicit client: _root_.clue.GraphQLStreamingClient[F, $schemaType]) =
                          client.subscribe(this)(Variables(...$variablesNames))
                      """
                  }

                // Congratulations! You got a full-fledged GraphQLOperation (hopefully).
                val result =
                  q"""
                $objMods object $objName extends { ..$objEarlyDefs } with ..$objParents { $objSelf =>
                  ..$objDefs

                  ..$variablesDefs

                  ..$dataDefs

                  $variablesEncoderDef
                  $dataDecoderDef

                  $convenienceMethodDef
                }
              """

                if (params.debug) log(result)

                result
            }

          case _ =>
            abort(
              "Invalid annotation target: must be an object extending GraphQLOperation[Schema]"
            )
        }
    }
}
