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

@compileTimeOnly("Macro annotations must be enabled")
class QueryTypes(schema: String) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro QueryTypesImpl.expand
}

private[clue] final class QueryTypesImpl(val c: blackbox.Context) {
  import c.universe._

  private[this] val macroName: Tree = {
    c.prefix.tree match {
      case Apply(Select(New(name), _), _) => name
      case _                              => c.abort(c.enclosingPosition, "Unexpected macro application")
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

  private[this] case class Typing(name: String, tpe: GType) {
    private def resolveType(tpe: GType): c.Tree =
      tpe match {
        case NullableType(tpe) => tq"Option[${resolveType(tpe)}]"
        case ListType(tpe)     => tq"List[${resolveType(tpe)}]"
        case nt: NamedType     => tq"${TypeName(nt.name)}"
        case GNoType           => tq"Any"
      }

    def toTree: c.Tree = {
      val n = TermName(name)
      val t = tq"${resolveType(tpe)}"
      val d = EmptyTree
      q"val $n: $t = $d"
    }
  }
  private[this] case class CaseClass(name: String, params: List[Typing]) {
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
    classes: List[CaseClass] = List.empty,
    types:   List[Typing] = List.empty
  )

  private[this] def resolveTypes(query: Query, tpe: GType): List[CaseClass] = {

    def go(currentQuery: Query, currentType: GType): Resolve =
      currentQuery match {
        case Query.Select(name, args, Query.Empty)                    =>
          Resolve(types = List(Typing(name, currentType.field(name))))
        case Query.Select(name, args, Query.Group(queries))           =>
          val nextType = currentType.field(name)
          val next     = queries
            .map(q => go(q, nextType))
            .foldLeft(Resolve())((r1, r2) =>
              Resolve(r1.classes ++ r2.classes, r1.types ++ r2.types)
            )
          Resolve(classes = next.classes :+ CaseClass(name.capitalize, next.types))
        case Query.Select(name, args, select @ Query.Select(_, _, _)) =>
          go(Query.Select(name, args, Query.Group(List(select))), currentType)
        case _                                                        => Resolve()
      }

    go(query, tpe).classes
  }

  final def expand(annottees: Tree*): Tree =
    annottees match {
      case List(
            q"..$mods object $objName extends { ..$objEarlyDefs } with ..$objParents { $objSelf => ..$objDefs }"
          ) /* if object extends GraphQLQuery */ =>
        documentDef(objDefs) match {

          case None           =>
            c.abort(c.enclosingPosition,
                    "The GraphQLQuery must define a 'val document' with a literal String"
            )

          case Some(document) =>
            val schema =
              c.prefix.tree match {
                case q"new ${`macroName`}(${schema: String})" =>
                  val PathExtract(basePath) = c.enclosingPosition.source.path
                  val jFile                 = new java.io.File(s"$basePath/resources/$schema.schema.graphql")
                  val schemaString          = new File(jFile).slurp()
                  Schema(schemaString).right.get
                case _                                        => c.abort(c.enclosingPosition, "Missing Schema Name.")
              }

            val query = QueryParser.parseText(document).toOption.get._1

            val caseClasses     = resolveTypes(query, schema.queryType)
            val caseClassesDefs = caseClasses.map(_.toTree).flatten

            val rootName  = query match {
              case Query.Select(name, _, _) => TermName(name)
              case _                        => TermName("???")
            }
            val rootType  = TypeName(caseClasses.last.name)
            val rootParam = q"val $rootName: $rootType = $EmptyTree"

            val result =
              q"""
                $mods object $objName extends { ..$objEarlyDefs } with ..$objParents { $objSelf =>
                  ..$objDefs

                  ..$caseClassesDefs

                  // @Lenses
                  case class Variables(y: Int)
                  object Variables { val jsonEncoder: io.circe.Encoder[Variables] = io.circe.generic.semiauto.deriveEncoder[Variables] }

                  // @Lenses
                  case class Data($rootParam)
                  object Data { val jsonDecoder: io.circe.Decoder[Data] = io.circe.generic.semiauto.deriveDecoder[Data] }

                  implicit val varEncoder: io.circe.Encoder[Variables] = Variables.jsonEncoder
                  implicit val dataDecoder: io.circe.Decoder[Data]     = Data.jsonDecoder
                }
              """

            c.info(
              c.enclosingPosition,
              result.toString(),
              force = true
            )

            result
        }

      case _ =>
        c.abort(c.enclosingPosition,
                "Invalid annotation target: must be an object extending GraphQLQuery"
        )
    }
}
