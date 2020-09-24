package clue.macros

import cats.syntax.all._
import scala.annotation.StaticAnnotation
import scala.reflect.macros.blackbox
import scala.annotation.tailrec
import edu.gemini.grackle._
import edu.gemini.grackle.{ Type => GType }
import edu.gemini.grackle.{ NoType => GNoType }
import edu.gemini.grackle.{ TypeRef => GTypeRef }
import edu.gemini.grackle.UntypedOperation.UntypedQuery
import edu.gemini.grackle.UntypedOperation.UntypedMutation
import edu.gemini.grackle.UntypedOperation.UntypedSubscription

class GraphQL(
  val mappings: Map[String, String] = Map.empty,
  val eq:       Boolean = false,
  val show:     Boolean = false,
  val lenses:   Boolean = false,
  val reuse:    Boolean = false,
  val debug:    Boolean = false
) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro GraphQLImpl.expand
}
private[clue] final class GraphQLImpl(val c: blackbox.Context) extends GrackleMacro {
  import c.universe._

  /**
   * Extract the `document` contents from the `GraphQLQuery` the marcro was applied to.
   */
  @tailrec
  private[this] def documentDef(tree: List[c.Tree]): Option[String] =
    tree match {
      case Nil                                          => none
      case q"$mods val document: $tpe = $document" :: _ =>
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
    rootType: GType,
    mappings: Map[String, String]
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
            parAccum = List(
              ClassParam.fromGrackleType(nameOverride.getOrElse(name), nextType.dealias, mappings)
            )
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
   * Resolve the types of the operation's variable arguments.
   */
  private[this] def resolveVariables(
    schema:   Schema,
    vars:     List[Query.UntypedVarDef],
    mappings: Map[String, String]
  ): CaseClass = {
    val inputs = compileVarDefs(schema, vars)

    if (inputs.isLeft)
      abort(s"Error resolving operation input variables types [$vars]: [${inputs.left}]]")
    if (inputs.isBoth)
      log(s"Warning resolving operation input variables types [$vars]: [${inputs.left}]]")
    val inputValues = inputs.right.get

    CaseClass("Variables",
              inputValues.map(iv => ClassParam.fromGrackleType(iv.name, iv.tpe, mappings))
    )
  }

  // We cannot fully resolve types since we cannot evaluate in the current annotated context.
  // Therefore, the schema name is always treated as unqualified, and therefore must be unique
  // across the whole compilation unit.
  // See: https://stackoverflow.com/questions/19379436/cant-access-parents-members-while-dealing-with-macro-annotations
  private[this] def schemaType(list: List[Tree]): Option[Tree] =
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
        schemaType(objParents) match {
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
                val settings       = MacroSettings.fromCtxSettings(c.settings)
                // Get annotation parameters.
                val optionalParams = buildOptionalParams[GraphQLOptionalParams]
                val params         = optionalParams.resolve(settings)

                // Extend Types from schema object.
                val objParentsWithTypes =
                  objParents :+ Select(typeNameToTermName(schemaType), TypeName("Types"))

                // Parse schema and metadata.
                val schemaTypeName = unqualifiedType(schemaType) match {
                  case None       =>
                    abort(s"Could not extract unqualified type from schema type [$schemaType]")
                  case Some(name) => name
                }
                val schema         = retrieveSchema(settings.schemaDirs, schemaTypeName)

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
                    val dataClasses =
                      resolveData(operation.query, schema.queryType, params.mappings)
                    dataClasses
                      .map(
                        _.toTree(params.eq,
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
                val variablesClass      = resolveVariables(schema, operation.variables, params.mappings)
                val variablesDef        =
                  if (!hasVariablesClass)
                    variablesClass.toTree(params.eq,
                                          params.show,
                                          params.lenses,
                                          reuse = false,
                                          encoder = true
                    )
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
                    List(variablesClass.params.map(_.toTree))
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
                        def execute[F[_]](...$variablesParams)(implicit client: _root_.clue.GraphQLClient[F, $schemaType]) =
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
                    $objMods object $objName extends { ..$objEarlyDefs } with ..$objParentsWithTypes { $objSelf =>
                      ..$objDefs

                      ..$variablesDef

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
