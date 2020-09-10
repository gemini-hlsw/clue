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
import edu.gemini.grackle.Query
import edu.gemini.grackle.NullableType
import edu.gemini.grackle.ListType
import edu.gemini.grackle.{ NoType => GNoType }
import edu.gemini.grackle.ObjectType
import edu.gemini.grackle.InterfaceType
import edu.gemini.grackle.Value
import scala.annotation.meta.field

@compileTimeOnly("Macro annotations must be enabled")
class QueryTypes(schema: String, debug: Boolean = false) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro QueryTypesImpl.expand
}

private[clue] final class QueryTypesImpl(val c: blackbox.Context) {
  import c.universe._

  val TypeMappings: Map[String, String] =
    Map("ID" -> "String", "uuid" -> "java.util.UUID", "targetobjecttype" -> "String")

  private[this] def abort(msg: Any): Nothing =
    c.abort(c.enclosingPosition, msg.toString)

  private[this] def log(msg: Any): Unit =
    c.info(c.enclosingPosition, msg.toString, force = true)

  private[this] def debugTree(tree: c.Tree): Unit =
    log(c.universe.showRaw(tree))

  private[this] val macroName: Tree = {
    c.prefix.tree match {
      case Apply(Select(New(name), _), _) => name
      case _                              => abort("Unexpected macro application")
    }
  }

  @tailrec
  private[this] def documentDef(tree: List[c.Tree]): Option[String] =
    tree match {
      case Nil                                        => None
      case q"val document = ${document: String}" :: _ => Some(document)
      case _ :: tail                                  => documentDef(tail)
    }

  private[this] val PathExtract = "(.*/src/.*?/).*".r

  private[this] case class TypedPar(name: String, tpe: GType) {
    private def resolveType(tpe: GType): c.Tree = {

      def unqualify(tpe: String): c.Tree =
        tpe.split("\\.").toList match {
          case Nil           => abort("Empty type")
          case single :: Nil => tq"${TypeName(single)}"
          case list          =>
            val selectors = list.tail.init.map(s => TermName(s)) :+ TypeName(list.last)
            tq"${selectors.foldLeft[c.Tree](Ident(TermName(list.head)))(Select.apply)}"
        }

      tpe match {
        case NullableType(tpe) => tq"Option[${resolveType(tpe)}]"
        case ListType(tpe)     => tq"List[${resolveType(tpe)}]"
        case nt: NamedType     => unqualify(TypeMappings.getOrElse(nt.name, nt.name))
        case GNoType           => tq"Any"
      }
    }

    def toTree: c.Tree = {
      // log(s"Resolving typed par: [$this]")
      val n = TermName(name)
      val t = tq"${resolveType(tpe)}"
      val d = EmptyTree
      q"val $n: $t = $d"
    }
  }
  private[this] case class CaseClass(name: String, params: List[TypedPar]) {
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
  private[this] case class Resolve(
    classes:  List[CaseClass] = List.empty,
    parAccum: List[TypedPar] = List.empty,
    vars:     List[TypedPar] = List.empty
  )

  private[this] def resolveTypes(query: Query, tpe: GType): Resolve = {

    def go(currentQuery: Query, currentType: GType): Resolve = {

      def fieldArgs(fieldName: String, args: List[Query.Binding]): List[TypedPar] =
        // log(s"NAME: [$fieldName] - ARGS : [$args]")
        currentType match {
          case ObjectType(_, _, fields, _) =>
            // log(s"NAME: [$fieldName] - ARGS : [$args] - FIELDS: [$fields]")
            args.collect { case Query.Binding(parName, Value.UntypedVariableValue(varName)) =>
              val typedParOpt =
                fields
                  .find(_.name == fieldName)
                  .flatMap(field =>
                    field.args.find(_.name == parName).map(input => TypedPar(varName, input.tpe))
                  )
              if (typedParOpt.isEmpty)
                abort(s"Unexpected binding [$parName] in selector [$fieldName]")
              typedParOpt.get
            }
          case _                           =>
            // log(s"Not resolved args [$args] with currentType [$currentType]")
            List.empty // TODO This is a query error if args.nonEmpty, report it.
        }

      currentQuery match {
        case (Query.Select(name, args, Query.Empty))                  => // Leaf
          Resolve(parAccum = List(TypedPar(name, currentType.field(name).dealias)),
                  vars = fieldArgs(name, args)
          )
        case Query.Select(name, args, Query.Group(queries))           => // Intermediate
          val nextType = currentType.field(name).underlyingObject
          // log(nextType)
          val next     = queries
            .map(q => go(q, nextType))
            .foldLeft(Resolve())((r1, r2) =>
              Resolve(r1.classes ++ r2.classes, r1.parAccum ++ r2.parAccum, r1.vars ++ r2.vars)
            )
          Resolve(
            classes = next.classes :+ CaseClass(name.capitalize, next.parAccum),
            vars = fieldArgs(name, args) ++ next.vars
          )
        case Query.Select(name, args, select @ Query.Select(_, _, _)) => // Intermediate with 1 Leaf
          go(Query.Select(name, args, Query.Group(List(select))), currentType)
        case _                                                        => Resolve()
      }
    }

    // log(query)
    // log(tpe.dealias.getClass())
    go(query, tpe.underlyingObject)
  }

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

  final def expand(annottees: Tree*): Tree =
    annottees match {
      case List(
            q"..$mods object $objName extends { ..$objEarlyDefs } with ..$objParents { $objSelf => ..$objDefs }"
          ) /* if object extends GraphQLQuery */ =>
        documentDef(objDefs) match {

          case None =>
            abort("The GraphQLQuery must define a 'val document' with a literal String")

          case Some(document) =>
            val (schema, debug) =
              c.prefix.tree match {
                case q"new ${`macroName`}(${schema: String})"                    =>
                  (retrieveSchema(schema), false)
                case q"new ${`macroName`}(${schema: String}, ${debug: Boolean})" =>
                  (retrieveSchema(schema), debug)
                case _                                                           =>
                  abort(
                    "Use @QueryTypes(<schemaName>) or @QueryTypes(<schemaName>, <debug>) (without naming parameters)."
                  )
              }

            val queryResult = QueryParser.parseText(document)
            if (queryResult.isLeft)
              abort(
                s"Could not parse document: ${queryResult.left.get.toChain.map(_.toString).toList.mkString("\n")}"
              )
            if (queryResult.isBoth)
              log(
                s"Warning parsing document: ${queryResult.left.get.toChain.map(_.toString).toList.mkString("\n")}"
              )
            val query       = queryResult.toOption.get._1

            // log(query)

            val resolvedTypes = resolveTypes(query, schema.queryType)

            val caseClasses     = resolvedTypes.classes
            val caseClassesDefs = caseClasses.map(_.toTree).flatten

            // log(resolvedTypes.vars)

            val rootName  = query match {
              case Query.Select(name, _, _) => TermName(name)
              case _                        => TermName("???")
            }
            val rootType  = TypeName(caseClasses.last.name)
            val rootParam = q"val $rootName: $rootType = $EmptyTree"

            val variables = resolvedTypes.vars.map(_.toTree)

            val result =
              q"""
                $mods object $objName extends { ..$objEarlyDefs } with ..$objParents { $objSelf =>
                  ..$objDefs

                  ..$caseClassesDefs

                  // @Lenses
                  case class Variables(..$variables)
                  object Variables { val jsonEncoder: io.circe.Encoder[Variables] = io.circe.generic.semiauto.deriveEncoder[Variables] }

                  // @Lenses
                  case class Data($rootParam)
                  object Data { val jsonDecoder: io.circe.Decoder[Data] = io.circe.generic.semiauto.deriveDecoder[Data] }

                  implicit val varEncoder: io.circe.Encoder[Variables] = Variables.jsonEncoder
                  implicit val dataDecoder: io.circe.Decoder[Data]     = Data.jsonDecoder
                }
              """

            if (debug) log(result)

            result
        }

      case _ =>
        abort("Invalid annotation target: must be an object extending GraphQLQuery")
    }
}
