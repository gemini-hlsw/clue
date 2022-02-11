// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.gen

import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import edu.gemini.grackle.UntypedOperation._
import edu.gemini.grackle.{ Term => _, Type => GType, _ }

import scala.meta._

trait QueryGen extends Generator {

  // TODO This could be more sophisticated.
  protected def extractSchemaType(list: List[Init]): Option[Type.Name] =
    list.collect {
      case Init(
            Type.Apply(Type.Name("GraphQLOperation"), List(tpe @ Type.Name(_))),
            _,
            Nil
          ) =>
        tpe
    }.headOption

  // TODO Support concatenation and stripMargin?
  // Actually when we support gql"" that should delimit things...
  // Actually(2)... Scalafix runs in a completely different context than the actual code.
  // Therefore, the only way to see the whole evaluated document from scalafix would be
  // to use a macro expansion to solve the document, and this would ONLY WORK IF semanticdb
  // kicked in after macro expansion, and not before.
  // Actually(3)... We are out of luck, scalafix doesn't see macro expansions:
  // https://scalacenter.github.io/scalafix/docs/developers/semantic-tree.html#macros
  protected def extractDocument(stats: List[Stat]): Option[String] =
    stats.collectFirst {
      case Defn.Val(_, List(Pat.Var(Term.Name(valName))), _, Lit.String(value))
          if valName == "document" =>
        value
    }

  protected def addImports(schemaName: String): List[Stat] => List[Stat] =
    parentBody => {
      val termName = Term.Name(schemaName)
      // TODO Consider cases where schemaname comes "applied"
      List(
        q"import $termName.Scalars._",
        q"ignoreUnusedImportScalars()",
        q"import $termName.Enums._",
        q"ignoreUnusedImportEnums()",
        q"import $termName.Types._",
        q"ignoreUnusedImportTypes()"
      ) ++ parentBody
    }

  //
  // START COPIED FROM GRACKLE.
  //
  import Query.{ Skip => _, _ }
  protected[this] def compileVarDefs(
    schema:         Schema,
    untypedVarDefs: UntypedVarDefs
  ): Result[VarDefs] =
    untypedVarDefs.traverse { case UntypedVarDef(name, untypedTpe, default) =>
      compileType(schema, untypedTpe).map(tpe => InputValue(name, None, tpe, default))
    }

  protected[this] def compileType(schema: Schema, tpe: Ast.Type): Result[GType] = {
    def loop(tpe: Ast.Type, nonNull: Boolean): Result[GType] = tpe match {
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
   * Resolve the types of the operation's variable arguments.
   */
  private[this] def resolveVariables(
    schema: Schema,
    vars:   List[Query.UntypedVarDef]
  ): CaseClass = {
    val inputs = compileVarDefs(schema, vars)

    if (inputs.isLeft)
      abort(
        s"Error resolving operation input variables types [${vars
            .map(v => s"${v.name}: ${v.tpe.name}")}]: [${inputs.left.get.toList.mkString("; ")}]]"
      )
        .unsafeRunSync()
    if (inputs.isBoth)
      log(
        s"Warning resolving operation input variables types [${vars
            .map(v => s"${v.name}: ${v.tpe.name}")}]: [${inputs.left.get.toList.mkString("; ")}]]"
      )
        .unsafeRunSync()

    CaseClass(
      "Variables",
      inputs.right.get.map(iv => ClassParam.fromGrackleType(iv.name, iv.tpe, isInput = true))
    )
  }

  import DefineType._
  protected def addVars(
    schema:    Schema,
    operation: UntypedOperation,
    config:    GraphQLGenConfig
  ): List[Stat] => List[Stat] =
    parentBody =>
      mustDefineType("Variables")(parentBody) match {
        case Skip            =>
          addModuleDefs("Variables",
                        config.catsEq,
                        config.catsShow,
                        scalaJSReactReuse = false,
                        circeEncoder = true
          )(
            parentBody
          )
        case Define(_, _, _) => // For now, we don't allow specifying Variables class parents.
          resolveVariables(schema, operation.variables).addToParentBody(
            config.catsEq,
            config.catsShow,
            config.monocleLenses,
            scalaJSReactReuse = false,
            circeEncoder = true,
            forceModule = true
          )(parentBody)
      }

  protected type ClassAccumulator = Accumulator[Class, ClassParam, Sum]

  protected object ClassAccumulator {
    def apply(
      classes:  List[Class] = List.empty,
      parAccum: List[ClassParam] = List.empty,
      sum:      Option[Sum] = None
    ): ClassAccumulator = new ClassAccumulator(classes, parAccum, sum)
  }

  protected implicit class ClassAccumulatorOps(classAccumulator: ClassAccumulator) {
    def withOverrideParams: ClassAccumulator =
      new ClassAccumulator(classAccumulator.classes,
                           parAccum = classAccumulator.parAccum.map(_.copy(overrides = true)),
                           classAccumulator.sum
      )
  }

  /**
   * Recurse the query AST and collect the necessary [[CaseClass]] es to hold its results.
   */
  protected def resolveData(
    schema:   Schema,
    algebra:  Query,
    rootType: Option[GType]
  ): CaseClass = {
    import Query._

    def getType(typeName: String): NamedType =
      schema
        .definition(typeName)
        .getOrElse(
          abort(s"Undefined type [$typeName] in inline fragment").unsafeRunSync()
        )

    def go(
      currentAlgebra: Query,
      currentType:    Option[GType],
      nameOverride:   Option[String] = none
    ): ClassAccumulator =
      currentAlgebra match {
        case Select(name, _, child) =>
          val paramName = nameOverride.getOrElse(name)

          MetaTypes
            .get(name)
            .orElse(currentType.flatMap(_.field(name)))
            .fold(
              throw new Exception(
                s"Could not resolve type for field [$name] - Is this a valid field present in the schema?"
              )
            ) { nextType =>
              val accumulatorOpt =
                nextType.underlyingObject.map(baseType => go(child, baseType.some))

              val (newClass, paramTypeNameOverride) =
                accumulatorOpt.fold[(Option[Class], Option[String])]((none, none))(next =>
                  next.sum.fold[(Option[Class], Option[String])](
                    (CaseClass(paramName, next.parAccum, next.classes).some, paramName.some)
                  )(sum => (SumClass(paramName, sum).some, paramName.some))
                )

              ClassAccumulator(
                classes = newClass.toList,
                parAccum = List(
                  ClassParam.fromGrackleType(paramName,
                                             nextType.dealias,
                                             isInput = false,
                                             paramTypeNameOverride
                  )
                )
              )
            }

        case UntypedNarrow(typeName, child) =>
          // Single element in inline fragment
          go(child, getType(typeName).some)
        case Rename(name, child)            =>
          go(child, currentType, name.some)
        case Group(selections)              =>
          // A Group in an inline fragment "... on X" will be represented as Group(List(UntypedNarrow(X, ...), UntypedNarrow(X, ...))).
          // We fix that to UntypedNarrow(X, Group(List(..., ...)))
          val fixedSelections = selections.map(_ match {
            // We flatly assume that if the first element of a group is UntypedNarrow(X, ...), then all elements are.
            case Group(list @ UntypedNarrow(typeName, _) +: _) =>
              UntypedNarrow(typeName, Group(list.collect { case UntypedNarrow(_, child) => child }))
            case other                                         => other
          })

          // (Also, check what Grackle is returning when there's an interface)
          val hierarchyAccumulators =
            fixedSelections.zipWithIndex // We want to preserve order of appeareance
              .groupBy {
                _._1 match {
                  case UntypedNarrow(typeName, _) =>
                    typeName.some // Selection in inline fragment, group by discriminator.some
                  case _                          =>
                    none // Selection in base group, group by none
                }
              }
              .toList
              .sortBy(_._2.head._2)      // Sort by first appeareance of each subtype
              .map { // Resolve groups
                case (Some(typeName), subQueries) =>
                  (typeName.some, // Unwrap inline fragment selections
                   subQueries.collect { case (UntypedNarrow(_, child), idx) =>
                     (go(child, getType(typeName).some), idx)
                   }
                  )
                case (None, subQueries)           =>
                  (none, subQueries.map { case (q, idx) => (go(q, currentType), idx) })
              }

          val baseAccumulators    = hierarchyAccumulators.collectFirst { case (None, accumulators) =>
            accumulators
          }.orEmpty
          val subTypeAccumulators = hierarchyAccumulators.collect {
            case (Some(typeName), accumulators) =>
              (typeName,
               ClassAccumulator(
                 accumulators.map(_._1).combineAll.classes,
                 (baseAccumulators.map { case (accumlator, idx) =>
                   (accumlator.withOverrideParams, idx)
                 } ++ accumulators).sortBy(_._2).map(_._1).combineAll.parAccum
               )
              )
          }
          val baseAccumulator     = baseAccumulators.map(_._1).combineAll

          subTypeAccumulators match {
            case Nil                  => baseAccumulator // No subtypes.
            case singleSubType :: Nil =>                 // Treat single subtype as regular group.
              ClassAccumulator(baseAccumulator.classes ++ singleSubType._2.classes,
                               singleSubType._2.parAccum
              )
            case _                    =>
              val baseParams = baseAccumulator.parAccum

              val subTypes = subTypeAccumulators.collect { case (typeName, accumulator) =>
                CaseClass(typeName, accumulator.parAccum, accumulator.classes)
              }

              ClassAccumulator(
                baseAccumulator.classes,
                baseParams,
                Sum(baseParams, baseAccumulator.classes, subTypes).some
              )
          }

        case Empty => ClassAccumulator()
        case _     =>
          log(s"Unhandled Algebra: [$algebra]").unsafeRunSync()
          ClassAccumulator()
      }

    val algebraTypes = go(algebra, rootType.flatMap(_.underlyingObject))

    CaseClass("Data", algebraTypes.parAccum, algebraTypes.classes)
  }

  protected def addData(
    schema:    Schema,
    operation: UntypedOperation,
    config:    GraphQLGenConfig
  ): List[Stat] => List[Stat] =
    parentBody =>
      mustDefineType("Data")(parentBody) match {
        case Skip            =>
          addModuleDefs("Data",
                        config.catsEq,
                        config.catsShow,
                        config.scalaJSReactReuse,
                        circeDecoder = true
          )(parentBody)
        case Define(_, _, _) => // For now, we don't allow specifying Data class parents.
          // For some reason, schema.schemaType only returns the Query type.
          val schemaType = schema.definition("Schema").getOrElse(schema.defaultSchemaType)

          // Leaving this comment in order to reproduce the issue.
          // log(schema.schemaType.asInstanceOf[ObjectType].fields).unsafeRunSync()
          // log(schema.definition("Schema").getOrElse(schema.defaultSchemaType)
          //     .asInstanceOf[ObjectType].fields).unsafeRunSync()

          val rootType = operation match {
            // This is how things should look like, but for some reason it's not working.
            // case _: UntypedQuery        => schema.queryType
            // case _: UntypedMutation     => schema.mutationType
            // case _: UntypedSubscription => schema.subscriptionType
            case _: UntypedQuery        => schemaType.field("query").flatMap(_.asNamed)
            case _: UntypedMutation     => schemaType.field("mutation").flatMap(_.asNamed)
            case _: UntypedSubscription => schemaType.field("subscription").flatMap(_.asNamed)
          }

          resolveData(schema, operation.query, rootType).addToParentBody(
            config.catsEq,
            config.catsShow,
            config.monocleLenses,
            config.scalaJSReactReuse,
            circeDecoder = true,
            forceModule = true
          )(parentBody)
      }

  private def isTermDefined(termName: String): List[Stat] => Boolean =
    parentBody =>
      parentBody.exists {
        // We are not checking in pattern assignments
        // case q"$_ val $tname: $_ = $_" => tname == tpe
        case Defn.Val(_, List(Pat.Var(Term.Name(name))), _, _) => name == termName
        // case q"$_ var $tname: $_ = $_" => tname == tpe
        case Defn.Var(_, List(Pat.Var(Term.Name(name))), _, _) => name == termName
        case _                                                 => false
      }

  private def addValDef(
    valName: String,
    valType: Type,
    value:   Term
  ): List[Stat] => List[Stat] =
    parentBody =>
      if (isTermDefined(valName)(parentBody))
        parentBody
      else
        parentBody :+ q"val ${Pat.Var(Term.Name(valName))}: $valType = $value"

  private def addValRefIntoModule(
    valName:       String,
    moduleName:    String,
    moduleValName: String,
    tpe:           Type
  ): List[Stat] => List[Stat] =
    parentBody =>
      parentBody
        .collectFirst {
          // case q"$_ object $tname extends { ..$_ } with ..$_ { $_ => ..$dataBody }"
          case Defn.Object(_, Term.Name(name), Template(_, _, _, dataBody)) if name == moduleName =>
            dataBody
        }
        .filter(isTermDefined(moduleValName))
        .map(_ =>
          addValDef(valName, tpe, Term.Select(Term.Name(moduleName), Term.Name(moduleValName)))(
            parentBody
          )
        )
        .getOrElse(parentBody)

  protected val addVarEncoder: List[Stat] => List[Stat] =
    addValRefIntoModule("varEncoder",
                        "Variables",
                        "jsonEncoderVariables",
                        t"io.circe.Encoder[Variables]"
    )

  protected val addDataDecoder: List[Stat] => List[Stat] =
    addValRefIntoModule("dataDecoder", "Data", "jsonDecoderData", t"io.circe.Decoder[Data]")

  protected def addConvenienceMethod(
    schemaType: Type,
    operation:  UntypedOperation
  ): List[Stat] => List[Stat] =
    parentBody =>
      parentBody
        .collectFirst {
          // case q"$_ class Variables $_(...$paramss) extends { ..$_ } with ..$_ { $_ => ..$_ }" =>
          case Defn.Class(_, Type.Name(name), _, Ctor.Primary(_, _, paramss), _)
              if name == "Variables" =>
            // Strip "val" from mods.
            paramss.map(_.map {
              case Term.Param(_, name, decltpe, default) => param"$name: $decltpe = $default"
              case other                                 => throw new Exception(s"Unexpected param structure [$other]")
            })
        }
        .map { paramss =>
          val variablesNames = paramss.map(_.map {
            case Term.Param(_, Name(name), _, _) => Term.Name(name)
            case other                           => throw new Exception(s"Unexpected param structure [$other]")
          })
          parentBody :+
            (operation match {
              case _: UntypedQuery        =>
                val allParamss = paramss :+ List(
                  param"implicit client: clue.TransactionalClient[F, $schemaType]"
                )
                q"def query[F[_]](...$allParamss) = client.request(this)(Variables(...$variablesNames))"
              case _: UntypedMutation     =>
                val allParamss = paramss :+ List(
                  param"implicit client: clue.TransactionalClient[F, $schemaType]"
                )
                q"def execute[F[_]](...$allParamss) = client.request(this)(Variables(...$variablesNames))"
              case _: UntypedSubscription =>
                val allParamss = paramss :+ List(
                  param"implicit client: clue.StreamingClient[F, $schemaType]"
                )
                q"def subscribe[F[_]](...$allParamss) = client.subscribe(this)(Variables(...$variablesNames))"
            })
        }
        .getOrElse(parentBody)

}
