package clue.macros

import scala.annotation.StaticAnnotation
import scala.reflect.macros.blackbox
import scala.annotation.tailrec
import scala.annotation.compileTimeOnly
import scala.reflect.io.File
import edu.gemini.grackle.Schema
import edu.gemini.grackle.TypeWithFields
import edu.gemini.grackle.ScalarType
import edu.gemini.grackle.QueryCompiler
import edu.gemini.grackle.QueryParser
import edu.gemini.grackle.NamedType
import edu.gemini.grackle.{ Type => GType }
import edu.gemini.grackle.UntypedOperation
import edu.gemini.grackle.NullableType
import edu.gemini.grackle.ListType
import edu.gemini.grackle.{ NoType => GNoType }
import edu.gemini.grackle.ObjectType
import edu.gemini.grackle.InterfaceType
import edu.gemini.grackle.Value
import scala.annotation.meta.field
import scala.annotation.Annotation
import edu.gemini.grackle.GraphQLParser
import edu.gemini.grackle.Ast
import edu.gemini.grackle.Query
import edu.gemini.grackle.Ast.Type.Named
import edu.gemini.grackle.Ast.Name

// Parameters must match exactly between this class and annotation class.
class GraphQLParams(val schema: String, val debug: Boolean = false) extends Annotation

// @compileTimeOnly("Macro annotations must be enabled")
class GraphQL(schema: String, debug: Boolean = false)
    extends GraphQLParams(schema, debug)
    with StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro GraphQLImpl.expand
}

private[clue] final class GraphQLImpl(val c: blackbox.Context) {
  import c.universe._

  val TypeMappings: Map[String, String] =
    Map("ID" -> "String", "uuid" -> "java.util.UUID", "targetobjecttype" -> "String")

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
      case Nil                              => None
      case q"val document = $document" :: _ =>
        scala.util.Try(c.eval(c.Expr[String](document))).toOption
      case _ :: tail                        => documentDef(tail)
    }

  /**
   * Attempt to guess the root directory for the project.
   * Assumes `.../project[/module]/src/[main|test|*]/` dir structure.
   */
  private[this] val PathExtract = "(.*/src/.*?/).*".r

  /**
   * Represents a parameter that will be used for a generated case class or variable.
   *
   * Consists of the name of the parameter and its Grackle type.
   */
  private[this] case class ClassParam(name: String, tpe: GType) {
    private def resolveType(tpe: GType): c.Tree =
      tpe match {
        case NullableType(tpe) => tq"Option[${resolveType(tpe)}]"
        case ListType(tpe)     => tq"List[${resolveType(tpe)}]"
        case nt: NamedType     => parseType(TypeMappings.getOrElse(nt.name, nt.name.capitalize))
        case GNoType           => tq"io.circe.Json"
      }

    def toTree: c.Tree = {
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
    def toTree: List[c.Tree] = {
      val n = TypeName(name)
      val o = TermName(name)
      // @Lenses
      List(
        q"""case class $n(...${List(params.map(_.toTree))})""",
        q"""object $o {implicit val jsonDecoder: io.circe.Decoder[$n] = io.circe.generic.semiauto.deriveDecoder[$n]}"""
      )
    }
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
      nameOverride:   Option[String] = None
    ): Resolve =
      currentAlgebra match {
        case Select(name, args, child) => // Intermediate
          val nextType = currentType.field(name)
          val baseType = nextType.underlyingObject
          val next     = go(child, baseType)
          val newClass = next.parAccum match {
            case Nil  => None
            case pars =>
              val caseClassName = baseType.asNamed
                .map(_.name.capitalize)
                .getOrElse(abort(s"Unexpected unnamed underlying type for [$baseType]"))
              Some(CaseClass(caseClassName, next.parAccum))
          }
          Resolve(
            classes = next.classes ++ newClass,
            parAccum = List(ClassParam(nameOverride.getOrElse(name), nextType.dealias))
          )
        case Rename(name, child)       =>
          go(child, currentType, Some(name))
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
    private def resolveType(tpe: Ast.Type, isOptional: Boolean = true): c.Tree =
      tpe match {
        case Ast.Type.Named(astName) =>
          val baseType = parseType(TypeMappings.getOrElse(astName.value, astName.value.capitalize))
          if (isOptional) tq"Option[$baseType]" else baseType
        case Ast.Type.List(ofType)   => tq"List[${resolveType(tpe, isOptional)}]"
        case Ast.Type.NonNull(of)    => resolveType(of.merge, isOptional = false)
      }

    def toTree: c.Tree = {
      val n = TermName(name)
      val t = tq"${resolveType(tpe)}"
      val d = EmptyTree
      q"val $n: $t = $d"
    }
  }

  /**
   * Resolve the types of the operation's variable arguments.
   */
  // def resolveVariables(opName: String, opArgs: List[Query.Binding]): List[ClassParam] =
  private[this] def resolveVariables(vars: List[Query.UntypedVarDef]): List[Variable] =
    vars.map(varDef => Variable(varDef.name, varDef.tpe))

  /**
   * Parse the schema file.
   */
  private[this] def retrieveSchema(schemaName: String): Schema = {
    val PathExtract(basePath) = c.enclosingPosition.source.path
    val jFile                 = new java.io.File(s"$basePath/resources/$schemaName.schema.graphql")
    val schemaString          = new File(jFile).slurp()
    val schema                = Schema(schemaString)
    if (schema.isLeft)
      abort(
        s"Could not parse schema [$schemaName]: ${schema.left.get.toChain.map(_.toString).toList.mkString("\n")}"
      )
    if (schema.isBoth)
      log(
        s"Warning parsing schema [$schemaName]: ${schema.left.get.toChain.map(_.toString).toList.mkString("\n")}"
      )
    schema.right.get
  }

  /**
   * Actual macro application, generating case classes to hold the query results and its variables.
   */
  final def expand(annottees: Tree*): Tree =
    annottees match {
      case List(
            q"..$mods object $objName extends { ..$objEarlyDefs } with ..$objParents { $objSelf => ..$objDefs }"
          ) /* TODO if object extends GraphQLQuery */ =>
        documentDef(objDefs) match {

          case None =>
            abort(
              "The GraphQLQuery must define a 'val document' that can be evaluated at compile time."
            )

          case Some(document) =>
            // Get annotation parameters and parse schema.
            val (schema, debug) = c.prefix.tree match {
              case q"new ${macroName}(..$params)" =>
                val Ident(TypeName(macroClassName)) = macroName
                val paramsClassName                 = parseType(s"clue.macros.${macroName}Params")
                val annotationParams                =
                  c.eval(
                    c.Expr[GraphQLParams](c.untypecheck(q"new $paramsClassName(..$params)"))
                  )
                (retrieveSchema(annotationParams.schema), annotationParams.debug)
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

            // Resolve types needed for the query result and its variables.
            val caseClasses     = resolveQueryData(operation.query, schema.queryType)
            // Build AST to define case classes.
            val caseClassesDefs = caseClasses.map(_.toTree).flatten

            // Build Variables parameters.
            val variables = resolveVariables(operation.variables).map(_.toTree)

            // Congratulations! You got a full-fledged GraphQLQuery (hopefully).
            val result =
              q"""
                $mods object $objName extends { ..$objEarlyDefs } with ..$objParents { $objSelf =>
                  ..$objDefs

                  // @Lenses
                  case class Variables(..$variables)
                  object Variables { implicit val jsonEncoder: io.circe.Encoder[Variables] = io.circe.generic.semiauto.deriveEncoder[Variables] }

                  ..$caseClassesDefs

                  override val varEncoder: io.circe.Encoder[Variables] = Variables.jsonEncoder
                  override val dataDecoder: io.circe.Decoder[Data]     = Data.jsonDecoder
                }
              """

            if (debug) log(result)

            result
        }

      case _ =>
        abort("Invalid annotation target: must be an object extending GraphQLQuery")
    }
}
