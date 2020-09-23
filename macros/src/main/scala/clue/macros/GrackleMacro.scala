package clue.macros

import cats.syntax.all._
import scala.reflect.macros.blackbox
import edu.gemini.grackle._

protected[macros] trait GrackleMacro extends Macro {
  val c: blackbox.Context

  import c.universe.{ Type => _, NoType => _, _ }

  /**
   * Represents a parameter that will be used for a generated case class or variable.
   *
   * Consists of the name of the parameter and its Grackle type.
   */
  protected[this] case class ClassParam(name: String, tpe: Type) {
    def toTree(mappings: Map[String, String]): ValDef = {
      def resolveType(tpe: Type): Tree =
        tpe match {
          case NullableType(tpe) => tq"Option[${resolveType(tpe)}]"
          case ListType(tpe)     => tq"List[${resolveType(tpe)}]"
          case nt: NamedType     => parseType(mappings.getOrElse(nt.name, nt.name.capitalize))
          case NoType            => tq"io.circe.Json"
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
  protected[this] case class CaseClass(name: String, params: List[ClassParam]) {

    def toTree(
      mappings: Map[String, String],
      eq:       Boolean,
      show:     Boolean,
      lenses:   Boolean,
      reuse:    Boolean,
      encoder:  Boolean = false,
      decoder:  Boolean = false
    ): List[Tree] =
      List(
        caseClassDef(name, params.map(_.toTree(mappings)), lenses),
        moduleDef(name, eq, show, reuse, encoder, decoder)
      )
  }

  //
  // START COPIED FROM GRACKLE.
  //
  import Query._
  protected[this] def compileVarDefs(
    schema:         Schema,
    untypedVarDefs: UntypedVarDefs
  ): Result[VarDefs] =
    untypedVarDefs.traverse { case UntypedVarDef(name, untypedTpe, default) =>
      compileType(schema, untypedTpe).map(tpe => InputValue(name, None, tpe, default))
    }

  protected[this] def compileType(schema: Schema, tpe: Ast.Type): Result[Type] = {
    def loop(tpe: Ast.Type, nonNull: Boolean): Result[Type] = tpe match {
      case Ast.Type.NonNull(Left(named)) => loop(named, true)
      case Ast.Type.NonNull(Right(list)) => loop(list, true)
      case Ast.Type.List(elem)           =>
        loop(elem, false).map(e => if (nonNull) ListType(e) else NullableType(ListType(e)))
      case Ast.Type.Named(name)          =>
        schema.definition(name.value) match {
          case None      => QueryInterpreter.mkErrorResult(s"Undefine typed '${name.value}'")
          case Some(tpe) => (if (nonNull) tpe else NullableType(tpe)).rightIor
        }
    }
    loop(tpe, false)
  }
  //
  // END COPIED FROM GRACKLE.
  //
}
