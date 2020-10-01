package clue.macros

import cats.syntax.all._
import scala.reflect.macros.blackbox
import edu.gemini.grackle._
import scala.reflect.io.File
import java.io.{ File => JFile }
import java.util.regex.Pattern
import cats.effect.IO

protected[macros] trait GraphQLMacro extends Macro {
  val c: blackbox.Context

  import c.universe.{ Type => _, NoType => _, _ }

  protected[this] def snakeToCamel(s: String): String = {
    val wordPattern: Pattern = Pattern.compile("[a-zA-Z0-9]+(_|$)")
    wordPattern.matcher(s).replaceAll(_.group.stripSuffix("_").toLowerCase.capitalize)
  }

  private[this] def nestedTypeTree(nestTree: Tree, tpe: Tree): Tree =
    tpe match {
      case Ident(typeName)                  => Select(nestTree, typeName)
      case AppliedTypeTree(enclosing, list) =>
        AppliedTypeTree(enclosing, list.map(t => nestedTypeTree(nestTree, t)))
    }

  private[this] def qualifiedNestedType(nestTree: Option[Tree], tpe: Tree): Tree =
    nestTree.fold(tpe) { tree =>
      nestedTypeTree(tree, tpe)
    }

  /**
   * Represents a parameter that will be used for a generated case class or variable.
   *
   * Consists of the name of the parameter and its Grackle type.
   */
  protected[this] case class ClassParam(name: String, tpe: Tree) {

    def typeTree(nestTree: Option[Tree], nestedTypes: List[String]): Tree =
      nestTree match {
        case None => tpe
        case _    =>
          if (nestedTypes.contains(name))
            qualifiedNestedType(nestTree, tpe)
          else
            tpe
      }

    def toTree(nestTree: Option[Tree], nestedTypes: List[String]): ValDef = {
      val n = TermName(name)
      val t = typeTree(nestTree, nestedTypes)
      val d = tpe match {
        case tq"Option[$inner]" => q"None"
        case _                  => EmptyTree
      }
      q"val $n: $t = $d"
    }
  }

  protected[this] object ClassParam {
    def fromGrackleType(
      name:         String,
      tpe:          Type,
      mappings:     Map[String, String],
      nameOverride: Option[String] = None
    ): ClassParam = {
      def resolveType(tpe: Type): Tree =
        tpe match {
          case NullableType(tpe) => tq"Option[${resolveType(tpe)}]"
          case ListType(tpe)     => tq"List[${resolveType(tpe)}]"
          case nt: NamedType     =>
            parseType(mappings.getOrElse(nt.name, snakeToCamel(nameOverride.getOrElse(nt.name))))
          case NoType            => tq"io.circe.Json"
        }

      ClassParam(name, resolveType(tpe))
    }
  }

  /**
   * The definition of a case class to contain an object from the query response.
   *
   * Consists of the class name and its [[ClassParam]] parameters.
   */
  protected[this] case class CaseClass(
    name:   String,
    params: List[ClassParam],
    nested: List[CaseClass] = List.empty
  ) {
    private val camelName = snakeToCamel(name)

    def addToParentBody(
      eq:          Boolean,
      show:        Boolean,
      lenses:      Boolean,
      reuse:       Boolean,
      encoder:     Boolean = false,
      decoder:     Boolean = false,
      forceModule: Boolean = false,
      nestTree:    Option[Tree] = None
    ): List[Tree] => List[Tree] =
      parentBody => {
        val nextTree              = nestTree
          .fold[Tree](Ident(TermName(camelName)))(t => Select(t, TermName(camelName)))
          .some
        val nestedTypeNames       = nested.map(_.name)
        val (newBody, wasMissing) =
          addCaseClassDef(camelName, params.map(_.toTree(nextTree, nestedTypeNames)))(
            parentBody
          )
        if (wasMissing || forceModule)
          addModuleDefs(
            camelName,
            eq,
            show,
            reuse,
            encoder,
            decoder,
            modStatements = scala.Function.chain(
              nested.map(
                _.addToParentBody(
                  eq,
                  show,
                  lenses,
                  reuse,
                  encoder,
                  decoder,
                  nestTree = nextTree
                )
              ) ++
                Option.when(lenses) { moduleBody =>
                  val lenses = params.map { param =>
                    val thisType  = qualifiedNestedType(nestTree, Ident(TypeName(camelName)))
                    val childType = param.typeTree(nextTree, nestedTypeNames)
                    q"val ${TermName(param.name)}: monocle.Lens[$thisType, $childType] = monocle.macros.GenLens[$thisType](_.${TermName(param.name)})"
                  }
                  moduleBody ++ lenses
                }
            ),
            nestTree = nestTree
          )(newBody)
        else
          newBody
      }
  }

  protected[this] case class Enum(name: String, values: List[String]) {
    def addToParentBody(
      eq:      Boolean,
      show:    Boolean,
      reuse:   Boolean,
      encoder: Boolean = false,
      decoder: Boolean = false
    ): List[Tree] => List[Tree] =
      addEnum(snakeToCamel(name), values.map(snakeToCamel), eq, show, reuse, encoder, decoder)
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
  protected[this] def retrieveSchema(resourceDirs: List[JFile], schemaName: String): IO[Schema] = {
    val fileName = s"$schemaName.graphql"
    resourceDirs.view.map(dir => new JFile(dir, fileName)).find(_.exists) match {
      case None             =>
        abort(s"No schema [$fileName] found in paths [${resourceDirs.mkString(", ")}]")
      case Some(schemaFile) =>
        IO(new File(schemaFile).slurp()).flatMap { schemaString =>
          val schema = Schema(schemaString)
          if (schema.isLeft)
            abort(
              s"Could not parse schema at [${schemaFile.getAbsolutePath}]: ${schema.left.get.toChain.map(_.toString).toList.mkString("\n")}"
            )
          else
            IO.whenA(schema.isBoth)(
              log(
                s"Warning when parsing schema [${schemaFile.getAbsolutePath}]: ${schema.left.get.toChain.map(_.toString).toList.mkString("\n")}"
              )
            ) >>
              IO.pure(schema.right.get)
        }
    }
  }
}
