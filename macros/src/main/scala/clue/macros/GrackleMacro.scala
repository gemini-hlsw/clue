package clue.macros

import cats.syntax.all._
import scala.reflect.macros.blackbox
import edu.gemini.grackle._
import scala.reflect.io.File
import java.io.{ File => JFile }
import scala.util.Success
import scala.util.Failure

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

  /**
   * Parse the schema file.
   */
  protected[this] def retrieveSchema(resourceDirs: List[JFile], schemaName: String): Schema = {
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
  protected[this] def retrieveSchemaMeta(
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
}
