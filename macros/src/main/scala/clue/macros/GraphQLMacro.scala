// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.macros

import cats.syntax.all._
import scala.reflect.macros.blackbox
import edu.gemini.grackle._
import scala.reflect.io.File
import java.io.{ File => JFile }
import cats.effect.IO

protected[macros] trait GraphQLMacro extends Macro {
  val c: blackbox.Context

  import c.universe.{ Type => _, NoType => _, _ }

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
  protected[this] case class ClassParam(name: String, tpe: Tree, overrides: Boolean = false) {

    def typeTree(nestTree: Option[Tree], nestedTypes: Map[String, Tree]): Tree =
      nestTree match {
        case None => tpe
        case _    => qualifiedNestedType(nestedTypes.get(name), tpe)
      }

    def toTree(
      nestTree:    Option[Tree],
      nestedTypes: Map[String, Tree],
      asVals:      Boolean = false
    ): ValDef = {
      val n = TermName(name)
      val t = typeTree(nestTree, nestedTypes)
      val d = tpe match {
        case tq"Option[$inner]"          => if (!asVals) q"None" else EmptyTree
        case tq"clue.data.Input[$inner]" => if (!asVals) q"clue.data.Undefined" else EmptyTree
        case _                           => EmptyTree
      }
      if (!asVals && overrides) q"override val $n: $t = $d" else q"val $n: $t = $d"
    }
  }

  protected[this] object ClassParam {
    def fromGrackleType(
      name:         String,
      tpe:          Type,
      isInput:      Boolean,
      mappings:     Map[String, String],
      nameOverride: Option[String] = None
    ): ClassParam = {
      def resolveType(tpe: Type): Tree =
        tpe match {
          case NullableType(tpe) =>
            if (isInput)
              tq"clue.data.Input[${resolveType(tpe)}]"
            else
              tq"Option[${resolveType(tpe)}]"
          case ListType(tpe)     => tq"List[${resolveType(tpe)}]"
          case nt: NamedType     =>
            parseType(mappings.getOrElse(nt.name, snakeToCamel(nameOverride.getOrElse(nt.name))))
          case NoType            => tq"io.circe.Json"
        }

      // log(s"Resolving Grackle type: [$tpe]").unsafeRunSync()

      ClassParam(name, resolveType(tpe))
    }
  }

  protected[this] sealed trait Class {
    val name: String

    protected val camelName = snakeToCamel(name)

    protected def nextNestPath(nestPath: Option[Tree]) = nestPath
      .fold[Tree](Ident(TermName(camelName)))(t => Select(t, TermName(camelName)))
      .some

    protected def nextNestedTypes(
      nested:      List[Class],
      nestPath:    Option[Tree],
      nestedTypes: Map[String, Tree]
    ): Map[String, Tree] =
      nestedTypes ++ nested.flatMap(c => nextNestPath(nestPath).map(path => c.name -> path))

    def addToParentBody(
      eq: Boolean,
      show:        Boolean,
      lenses:      Boolean,
      reuse:       Boolean,
      encoder:     Boolean = false,
      decoder:     Boolean = false,
      forceModule: Boolean = false,
      nestPath:    Option[Tree] = None,
      nestedTypes: Map[String, Tree] = Map.empty,
      extending:   Option[String] = None
    ): List[Tree] => List[Tree]
  }

  /**
   * The definition of a case class to contain an object from the query response.
   *
   * Consists of the class name and its [[ClassParam]] parameters.
   */
  protected[this] case class CaseClass(
    name:   String,
    params: List[ClassParam],
    nested: List[Class] = List.empty
  ) extends Class {

    def addToParentBody(
      eq:          Boolean,
      show:        Boolean,
      lenses:      Boolean,
      reuse:       Boolean,
      encoder:     Boolean = false,
      decoder:     Boolean = false,
      forceModule: Boolean = false,
      nestPath:    Option[Tree] = None,
      nestedTypes: Map[String, Tree] = Map.empty,
      extending:   Option[String] = None
    ): List[Tree] => List[Tree] =
      parentBody => {
        val nextPath  = nextNestPath(nestPath)
        val nextTypes = nextNestedTypes(nested, nestPath, nestedTypes)

        val (newBody, wasMissing) =
          addCaseClassDef(camelName, params.map(_.toTree(nextPath, nextTypes)), extending)(
            parentBody
          )

        val addNested = nested.map(
          _.addToParentBody(
            eq,
            show,
            lenses,
            reuse,
            encoder,
            decoder,
            nestPath = nextPath
          )
        )

        val addLenses = Option.when(lenses) { (moduleBody: List[Tree]) =>
          val lensesDef = params.map { param =>
            val thisType  = qualifiedNestedType(nestPath, Ident(TypeName(camelName)))
            val childType = param.typeTree(nextPath, nextTypes)
            q"val ${TermName(param.name)}: monocle.Lens[$thisType, $childType] = monocle.macros.GenLens[$thisType](_.${TermName(param.name)})"
          }
          moduleBody ++ lensesDef
        }

        if (wasMissing || forceModule)
          addModuleDefs(
            camelName,
            eq,
            show,
            reuse,
            encoder,
            decoder,
            modStatements = scala.Function.chain(addNested ++ addLenses),
            nestPath = nestPath
          )(newBody)
        else
          newBody
      }
  }

  protected[this] case class Sum(
    params:        List[ClassParam],
    nested:        List[Class] = List.empty,
    instances:     List[CaseClass],
    discriminator: String
  )

  protected[this] case class SumClass(
    name: String,
    sum:  Sum
  ) extends Class {

    override def addToParentBody(
      eq:          Boolean,
      show:        Boolean,
      lenses:      Boolean,
      reuse:       Boolean,
      encoder:     Boolean,
      decoder:     Boolean,
      forceModule: Boolean,
      nestPath:    Option[Tree] = None,
      nestedTypes: Map[String, Tree] = Map.empty,
      extending:   Option[String] = None
    ): List[Tree] => List[Tree] =
      parentBody => {
        val nextPath              = nextNestPath(nestPath)
        val nextTypes             = nextNestedTypes(sum.nested, nestPath, nestedTypes)
        val (newBody, wasMissing) =
          addSumTrait(camelName,
                      sum.params.map(_.toTree(nextPath, nextTypes, asVals = true)),
                      extending
          )(
            parentBody
          )

        val addDefinitions =
          sum.nested.map(
            _.addToParentBody(eq,
                              show,
                              lenses,
                              reuse,
                              encoder,
                              decoder,
                              forceModule,
                              nextPath,
                              nextTypes
            )
          ) ++
            sum.instances.map(
              _.addToParentBody(eq,
                                show,
                                lenses,
                                reuse,
                                encoder,
                                decoder,
                                forceModule,
                                nextPath,
                                nextTypes,
                                camelName.some // Extends
              )
            )

        val addLenses = Option.when(lenses) { (moduleBody: List[Tree]) =>
          val lensesDef = sum.params.map { param =>
            val paramNameDef = TermName(param.name)
            val thisType     = qualifiedNestedType(nestPath, Ident(TypeName(camelName)))
            val childType    = param.typeTree(nextPath, nextTypes)
            val cases        = sum.instances.map { cc =>
              val caseType = qualifiedNestedType(nextPath, Ident(TypeName(cc.name)))
              cq"s: $caseType => s.copy($paramNameDef = v)"
            }
            q"""val $paramNameDef: monocle.Lens[$thisType, $childType] = 
                  monocle.Lens[$thisType, $childType](_.$paramNameDef){ v =>
                    _ match {case ..$cases}
                  }"""
          }
          moduleBody ++ lensesDef
        }

        if (wasMissing || forceModule)
          addModuleDefs(
            camelName,
            eq,
            show,
            reuse,
            encoder,
            decoder,
            TypeType.Sum(sum.discriminator),
            modStatements = scala.Function.chain(addDefinitions ++ addLenses),
            nestPath = nestPath
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
      addEnum(snakeToCamel(name), values, eq, show, reuse, encoder, decoder)
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
