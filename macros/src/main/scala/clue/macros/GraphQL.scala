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
import scala.annotation.Annotation
import io.circe.parser.decode
import io.circe.ParsingFailure
import scala.util.Success
import scala.util.Failure
import edu.gemini.grackle.UntypedOperation.UntypedQuery
import edu.gemini.grackle.UntypedOperation.UntypedMutation
import edu.gemini.grackle.UntypedOperation.UntypedSubscription

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

private[clue] final class GraphQLImpl(val c: blackbox.Context) {
  import c.universe._

  /**
   * Abort the macro showing an error message.
   */
  private[this] def abort(msg: Any): Nothing =
    c.abort(c.enclosingPosition, msg.toString)

  /**
   * Log debug info.
   */
  private[this] def log(msg: Any): Unit =
    c.info(c.enclosingPosition, msg.toString, force = true)

  /**
   * Log an actual Tree AST (not the Scala code equivalent).
   */
  private[this] def debugTree(tree: c.Tree): Unit =
    log(c.universe.showRaw(tree))

  /**
   * Parse a type name (c.parse only parses terms).
   */
  def parseType(tpe: String): c.Tree =
    c.parse(tpe) match {
      case Ident(TermName(name))        => Ident(TypeName(name))
      case Select(tree, TermName(name)) => Select(tree, TypeName(name))
      case other                        =>
        debugTree(other)
        abort(s"Unexpected type [$tpe]")
    }

  /**
   * Extract the unqualified type name from a type.
   */
  def unqualifiedType(tpe: c.Tree): Option[String] = tpe match {
    case Ident(TypeName(schema))     => schema.some
    case Select(_, TypeName(schema)) => schema.some
    case _                           => none
  }

  /**
   * Parse an import name (only simple and wildcard supported, no groups or aliases).
   */
  def parseImport(imp: String): c.Tree = {
    val Wildcard   = "_WILDCARD_"
    val unwildcard = if (imp.trim.endsWith("_")) imp.trim.init + Wildcard else imp
    c.parse(unwildcard) match {
      case Ident(TermName(name))               => Import(Ident(TypeName(name)), List.empty)
      case Select(tree, term @ TermName(name)) =>
        val selector =
          if (name === Wildcard)
            ImportSelector(termNames.WILDCARD, -1, null, -1)
          else
            ImportSelector(term, -1, term, -1)
        Import(tree, List(selector))
      case other                               =>
        debugTree(other)
        abort(s"Unexpected import [$imp]")
    }
  }

  /**
   * Get the annotation name.
   */
  private[this] val macroName: Tree = {
    c.prefix.tree match {
      case Apply(Select(New(name), _), _) => name
      case _                              => abort("Unexpected macro application")
    }
  }

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
   * Compute a case class definition.
   */
  private[this] def caseClassDef(
    name:   String,
    pars:   List[c.universe.ValDef],
    lenses: Boolean
  ): c.Tree = {
    val n = TypeName(name)
    if (lenses)
      q"@monocle.macros.Lenses case class $n(..$pars)"
    else
      q"case class $n(..$pars)"
  }

  /**
   * Compute a companion object with typeclasses.
   */
  private[this] def moduleDef(
    name:    String,
    eq:      Boolean,
    show:    Boolean,
    reuse:   Boolean,
    encoder: Boolean = false,
    decoder: Boolean = false
  ): c.Tree = {
    val n = TypeName(name)

    val eqDef =
      if (eq) q"implicit val ${TermName(s"eq$name")}: cats.Eq[$n] = cats.Eq.fromUniversalEquals"
      else EmptyTree

    val showDef =
      if (show) q"implicit val ${TermName(s"show$name")}: cats.Show[$n] = cats.Show.fromToString"
      else EmptyTree

    val reuseDef =
      if (reuse)
        q"""implicit val ${TermName(s"reuse$name")}: japgolly.scalajs.react.Reusability[$n] = {
              import japgolly.scalajs.react.Reusability
              japgolly.scalajs.react.Reusability.derive
            }
          """
      else EmptyTree

    val encoderDef =
      if (encoder)
        q"implicit val ${TermName(s"jsonEncoder$name")}: io.circe.Encoder[$n] = io.circe.generic.semiauto.deriveEncoder[$n]"
      else EmptyTree

    val decoderDef =
      if (decoder)
        q"implicit val ${TermName(s"jsonDecoder$name")}: io.circe.Decoder[$n] = io.circe.generic.semiauto.deriveDecoder[$n]"
      else EmptyTree

    q"""object ${TermName(name)} {
          $eqDef
          $showDef
          $reuseDef
          $encoderDef
          $decoderDef
        }"""
  }

  /**
   * Represents a parameter that will be used for a generated case class or variable.
   *
   * Consists of the name of the parameter and its Grackle type.
   */
  private[this] case class ClassParam(name: String, tpe: GType) {
    def toTree(mappings: Map[String, String]): c.universe.ValDef = {

      def resolveType(tpe: GType): c.Tree =
        tpe match {
          case NullableType(tpe) => tq"Option[${resolveType(tpe)}]"
          case ListType(tpe)     => tq"List[${resolveType(tpe)}]"
          case nt: NamedType     => parseType(mappings.getOrElse(nt.name, nt.name.capitalize))
          case GNoType           => tq"io.circe.Json"
        }

      val n = TermName(name)
      val t = tq"${resolveType(tpe)}"
      val d = EmptyTree
      q"val $n: $t = $d"
    }
  }

  /**
   * The definition of a case class to contain an object from the query response.
   *
   * Consists of the class name and its [[ClassParam]] parameters.
   */
  private[this] case class CaseClass(name: String, params: List[ClassParam]) {
    def toTree(
      mappings: Map[String, String],
      eq:       Boolean,
      show:     Boolean,
      lenses:   Boolean,
      reuse:    Boolean
    ): List[c.Tree] =
      List(
        caseClassDef(name, params.map(_.toTree(mappings)), lenses),
        moduleDef(name, eq, show, reuse, decoder = true)
      )
  }

  /**
   * Recurse the query AST and collect the necessary [[CaseClass]]es to hold its results.
   *
   * `Resolve.parAccum` accumulates parameters unit we have a whole case class definition.
   * It should be empty by the time we are done.
   */
  private[this] def resolveQueryData(
    algebra:  Query,
    rootType: GType
  ): List[CaseClass] = {
    import Query._

    // Holds the aggregated [[CaseClass]]es and their [[ClassParam]]s as we recurse the query AST.
    case class Resolve(
      classes:  List[CaseClass] = List.empty,
      parAccum: List[ClassParam] = List.empty
    )

    def go(
      currentAlgebra: Query,
      currentType:    GType,
      nameOverride:   Option[String] = none
    ): Resolve =
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
          Resolve(
            classes = newClasses,
            parAccum = List(ClassParam(nameOverride.getOrElse(name), nextType.dealias))
          )
        case Rename(name, child)       =>
          go(child, currentType, name.some)
        case Group(selections)         =>
          selections
            .map(q => go(q, currentType))
            .foldLeft(Resolve())((r1, r2) =>
              Resolve(r1.classes ++ r2.classes, r1.parAccum ++ r2.parAccum)
            )
        case Empty                     => Resolve()
        case _                         =>
          log(s"Unhandled Algebra: [$algebra]")
          Resolve()
      }

    val algebraTypes = go(algebra, rootType.underlyingObject)

    algebraTypes.classes :+ CaseClass("Data", algebraTypes.parAccum)
  }

  private[this] case class Variable(name: String, tpe: Ast.Type) {

    def toTree(mappings: Map[String, String]): c.universe.ValDef = {

      def resolveType(tpe: Ast.Type, isOptional: Boolean = true): c.Tree =
        tpe match {
          case Ast.Type.Named(astName) =>
            val baseType = parseType(mappings.getOrElse(astName.value, astName.value.capitalize))
            if (isOptional) tq"Option[$baseType]" else baseType
          case Ast.Type.List(ofType)   => tq"List[${resolveType(tpe, isOptional)}]"
          case Ast.Type.NonNull(of)    => resolveType(of.merge, isOptional = false)
        }

      val n = TermName(name)
      val t = tq"${resolveType(tpe)}"
      val d = EmptyTree
      q"val $n: $t = $d"
    }
  }

  /**
   * Resolve the types of the operation's variable arguments.
   */
  private[this] def resolveVariables(vars: List[Query.UntypedVarDef]): List[Variable] =
    vars.map(varDef => Variable(varDef.name, varDef.tpe))

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
  // across the whole compilation.
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
            abort("Invalid annotation target: must be an object extending GraphQLOperation[Schema]")

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
                val optionalParams = c.prefix.tree match {
                  case q"new ${macroName}(..$params)" =>
                    val Ident(TypeName(macroClassName)) = macroName
                    val paramsClassName                 = parseType(s"clue.macros.${macroClassName}OptionalParams")
                    // Convert parameters to Some(...).
                    val optionalParams                  = params.map {
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

                // Build imports.
                val imports = schemaMeta.imports.map(parseImport)

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
                val dataDefs       =
                  if (!hasDataClass) {
                    // Resolve types needed for the query result and its variables.
                    val dataClasses = resolveQueryData(operation.query, schema.queryType)
                    dataClasses
                      .map(
                        _.toTree(schemaMeta.mappings,
                                 params.eq,
                                 params.show,
                                 params.lenses,
                                 params.reuse
                        )
                      )
                      .flatten
                  } else if (!hasDataModule)
                    List(moduleDef("Data", params.eq, params.show, params.reuse, decoder = true))
                  else
                    List.empty
                val dataDecoderDef =
                  if (!hasDataModule)
                    q"override val dataDecoder: io.circe.Decoder[Data]     = Data.jsonDecoderData"
                  else EmptyTree

                // Build AST to define case classe to hold Variables.
                val variables           =
                  resolveVariables(operation.variables).map(_.toTree(schemaMeta.mappings))
                val variablesClassDef   =
                  if (!hasVariablesClass) {
                    // Build Variables parameters.
                    caseClassDef("Variables", variables, params.lenses)
                  } else EmptyTree
                val variablesModuleDef  =
                  if (!hasVariablesModule)
                    moduleDef("Variables", params.eq, params.show, reuse = false, encoder = true)
                  else EmptyTree
                val variablesEncoderDef =
                  if (!hasVariablesModule)
                    q"override val varEncoder: io.circe.Encoder[Variables] = Variables.jsonEncoderVariables"
                  else EmptyTree

                // Build convenience method.
                val variableParams       = varParams.getOrElse(List(variables))
                val variableNames        = variableParams
                  .map(_.map { case q"$mods val $name: $tpt = $rhs" => name })
                val convenienceMethodDef =
                  operation match {
                    case _: UntypedQuery        =>
                      q"""
                        def query[F[_]](...$variableParams)(implicit client: _root_.clue.GraphQLClient[F, $schemaType]) =
                          client.request(this)(Variables(...$variableNames))
                      """
                    case _: UntypedMutation     =>
                      q"""
                        def mutate[F[_]](...$variableParams)(implicit client: _root_.clue.GraphQLClient[F, $schemaType]) =
                          client.request(this)(Variables(...$variableNames))
                      """
                    case _: UntypedSubscription =>
                      q"""
                        def subscribe[F[_]](...$variableParams)(implicit client: _root_.clue.GraphQLStreamingClient[F, $schemaType]) =
                          client.subscribe(this)(Variables(...$variableNames))
                      """
                  }

                // Congratulations! You got a full-fledged GraphQLOperation (hopefully).
                val result =
                  q"""
                $objMods object $objName extends { ..$objEarlyDefs } with ..$objParents { $objSelf =>
                  ..$imports

                  ..$objDefs

                  $variablesClassDef
                  $variablesModuleDef

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
            abort("Invalid annotation target: must be an object extending GraphQLOperation[Schema]")
        }
    }
}
