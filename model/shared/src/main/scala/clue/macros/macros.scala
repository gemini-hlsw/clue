package clue.macros

import scala.annotation.StaticAnnotation
import scala.reflect.macros.blackbox
import scala.annotation.tailrec
import scala.annotation.compileTimeOnly

@compileTimeOnly("Macro annotations must be enabled")
class QueryData(schemaModule: String) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro QueryDataImpl.expand
}

private[clue] final class QueryDataImpl(val c: blackbox.Context) {
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
            val mappings =
              c.prefix.tree match {
                case q"new ${`macroName`}(${mappingsModule: String})" =>
                  c.eval(c.Expr(Ident(c.mirror.staticModule(mappingsModule))))
                    .asInstanceOf[Mapping]
                    .mapping
                case _                                                => c.abort(c.enclosingPosition, "Unexpected missing Mapping object.")
              }

            val fields = List(
              document.split("\\n").toList.map(_.trim).filterNot(_.isEmpty).map { v =>
                // val t = TypeName("Int")
                val t = TypeName(mappings(v))
                val n = TermName(v)
                val d = EmptyTree
                q"val $n: $t = $d"
              }
            )

            q"""
              $mods object $objName extends { ..$objEarlyDefs } with ..$objParents { $objSelf =>
                ..$objDefs

                case class Variables(y: Int)
                object Variables { val jsonEncoder: io.circe.Encoder[Variables] = io.circe.generic.semiauto.deriveEncoder[Variables] }

                case class Data(...$fields)
                object Data { val jsonDecoder: io.circe.Decoder[Data] = io.circe.generic.semiauto.deriveDecoder[Data] }

                implicit val varEncoder: io.circe.Encoder[Variables] = Variables.jsonEncoder
                implicit val dataDecoder: io.circe.Decoder[Data]     = Data.jsonDecoder
              }
          """
        }

      case _ =>
        c.abort(c.enclosingPosition,
                "Invalid annotation target: must be an object extending GraphQLQuery"
        )
    }
}
