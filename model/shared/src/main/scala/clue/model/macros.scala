package clue

import scala.annotation.StaticAnnotation
import scala.reflect.macros.blackbox
import scala.annotation.tailrec

// class QueryData( /*schema: Class[_] = ???*/ ) extends StaticAnnotation {
class QueryData extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro QueryDataImpl.expand
}

private[clue] final class QueryDataImpl(val c: blackbox.Context) {
  import c.universe._

  private[this] def isCaseClassOrSealed(clsDef: ClassDef) =
    clsDef.mods.hasFlag(Flag.CASE) || clsDef.mods.hasFlag(Flag.SEALED)

  @tailrec
  private[this] def documentDef(tree: List[c.Tree]): Option[String] =
    tree match {
      case Nil                                        => None
      case q"val document = ${document: String}" :: _ => Some(document)
      case _ :: tail                                  => documentDef(tail)
    }

  final def expand(annottees: Tree*): Tree =
    // println(annottees)
    // q"""case class Data(hello: String)"""
    // val xxx =
    // q"..$mods object $objName extends { ..$objEarlyDefs } with ..$objParents { $objSelf => ..$objDefs }"
    // println(xxx)
    annottees match {
      // case List(clsDef: ClassDef) =>
      // clsDef.
      //   q"""
      //     case class Data(hello: String)
      //     $clsDef
      //     object ${clsDef.name.toTermName} {
      //       val x =
      //     }
      //   """

      case List(
            q"..$mods object $objName extends { ..$objEarlyDefs } with ..$objParents { $objSelf => ..$objDefs }"
          ) /* if object extends GraphQLQuery */ =>
        // val defs = TermName(objDefs.toString)
        documentDef(objDefs) match {

          case None           =>
            c.abort(c.enclosingPosition,
                    "The GraphQLQuery must define a 'val document' with a literal String"
            )

          case Some(document) =>
            val fields = List(
              document.split("\\n").toList.map(_.trim).filterNot(_.isEmpty).map { v =>
                val t = TypeName("Int")
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

      // case List(clsDef: ClassDef) =>
      //   q"""
      //  $clsDef
      //  object ${clsDef.name.toTermName} {
      //  }
      //  """
      // case List(
      //       clsDef: ClassDef,
      //       q"..$mods object $objName extends { ..$objEarlyDefs } with ..$objParents { $objSelf => ..$objDefs }"
      //     ) if isCaseClassOrSealed(clsDef) =>
      //   q"""
      //  $clsDef
      //  $mods object $objName extends { ..$objEarlyDefs } with ..$objParents { $objSelf =>
      //    ..$objDefs
      //    $clsDef
      //  }
      //  """

      case _ =>
        c.abort(c.enclosingPosition,
                "Invalid annotation target: must be an object extending GraphQLQuery"
        )
    }

  // def impl(c: whitebox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
  //   import c.universe._
  //   val inputs                = annottees.map(_.tree).toList
  //   val (annottee, expandees) = inputs match {
  //     case (param: ValDef) :: (rest @ (_ :: _))  => (param, rest)
  //     case (param: TypeDef) :: (rest @ (_ :: _)) => (param, rest)
  //     case _                                     => (EmptyTree, inputs)
  //   }
  //   println("IN MACRO!")
  //   println((annottee, expandees))
  //   val outputs               = expandees
  //   c.Expr[Any](Block(outputs, Literal(Constant(()))))
  // }
}
