// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.gen

import cats.data.State
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import grackle.UntypedOperation.*
import grackle.{Term as _, Type as GType, *}

import scala.meta.*

trait QueryGen extends Generator {

  // TODO This could be more sophisticated.
  protected def extractSchemaType(list: List[Init]): Option[Type.Name] =
    list.collect {
      case Init.Initial(
            Type.Apply.Initial(Type.Name("GraphQLOperation"), List(tpe @ Type.Name(_))),
            _,
            Nil
          ) =>
        tpe
    }.headOption

  protected def extractSchemaAndRootTypes(list: List[Init]): Option[(Type.Name, String)] =
    list.collect {
      case Init.Initial(
            Type.Apply.Initial(Type.Name("GraphQLSubquery"), List(tpe @ Type.Name(_))),
            _,
            List(List(Lit.String(rootType)))
          ) =>
        (tpe, rootType)
    }.headOption

  case class InterpolatedGql(parts: List[GqlPart]) {
    def render = parts
      .traverse {
        case GqlPart.Literal(value) => State.pure[Int, String](value)
        case _                      =>
          State.inspect[Int, String](i => s"{ subquery$i }") <* State.modify(_ + 1)
      }
      .runA(0)
      .value
      .mkString

    def subqueries = parts.collect { case GqlPart.Subquery(term) =>
      term
    }
  }

  sealed abstract class GqlPart
  object GqlPart {
    case class Literal(value: String) extends GqlPart
    case class Subquery(term: Term)   extends GqlPart
  }

  protected def extractDocument(stats: List[Stat]): Option[InterpolatedGql] =
    extractGql("document", stats)

  protected def extractSubquery(stats: List[Stat]): Option[InterpolatedGql] =
    extractGql("subquery", stats)

  // TODO Support concatenation and stripMargin?
  // Actually when we support gql"" that should delimit things...
  // Actually(2)... Scalafix runs in a completely different context than the actual code.
  // Therefore, the only way to see the whole evaluated document from scalafix would be
  // to use a macro expansion to solve the document, and this would ONLY WORK IF semanticdb
  // kicked in after macro expansion, and not before.
  // Actually(3)... We are out of luck, scalafix doesn't see macro expansions:
  // https://scalacenter.github.io/scalafix/docs/developers/semantic-tree.html#macros
  private def extractGql(typ: String, stats: List[Stat]): Option[InterpolatedGql] =
    stats.collectFirst {
      case Defn.Val(_, List(Pat.Var(Term.Name(valName))), _, Lit.String(value)) if valName == typ =>
        InterpolatedGql(List(GqlPart.Literal(value)))
      case Defn.Val(_,
                    List(Pat.Var(Term.Name(valName))),
                    _,
                    Term.Interpolate(_, rawLiterals, rawArgs)
          ) if valName == typ =>
        val literals: List[GqlPart] = rawLiterals.collect { case Lit.String(value) =>
          GqlPart.Literal(value)
        }

        val args: List[GqlPart] = rawArgs.map(GqlPart.Subquery(_))

        val parts = literals.map(Some(_)).zipAll(args.map(Some(_)), None, None).flatMap {
          case (literal, arg) =>
            List(literal, arg).flatten
        }

        InterpolatedGql(parts)
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
  import Query._
  protected[this] def compileVarDefs(
    schema:         Schema,
    untypedVarDefs: UntypedVarDefs
  ): Result[VarDefs] =
    untypedVarDefs.traverse { case UntypedVarDef(name, untypedTpe, default, directives) =>
      compileType(schema, untypedTpe).map(tpe => InputValue(name, None, tpe, default, directives))
    }

  protected[this] def compileType(schema: Schema, tpe: Ast.Type): Result[GType] = {
    def loop(tpe: Ast.Type, nonNull: Boolean): Result[GType] = tpe match {
      case Ast.Type.NonNull(Left(named)) => loop(named, nonNull = true)
      case Ast.Type.NonNull(Right(list)) => loop(list, nonNull = true)
      case Ast.Type.List(elem)           =>
        loop(elem, nonNull = false).map(e =>
          if (nonNull) ListType(e) else NullableType(ListType(e))
        )
      case Ast.Type.Named(name)          =>
        schema.definition(name.value) match {
          case None     => Result.internalError(s"Undefine typed '${name.value}'")
          case Some(tp) => Result.success(if (nonNull) tp else NullableType(tp))
        }
    }

    loop(tpe, nonNull = false)
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

    if (!inputs.hasValue)
      abort(
        s"Error resolving operation input variables types [${vars
            .map(v => s"${v.name}: ${v.tpe.name}")}]: [${inputs.toProblems.toList.mkString("; ")}]]"
      )
        .unsafeRunSync()
    if (inputs.hasProblems)
      log(
        s"Warning resolving operation input variables types [${vars
            .map(v => s"${v.name}: ${v.tpe.name}")}]: [${inputs.toProblems.toList.mkString("; ")}]]"
      )
        .unsafeRunSync()

    CaseClass(
      "Variables",
      inputs.toOption.get.map(iv => ClassParam.fromGrackleType(iv.name, iv.tpe, isInput = true))
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
          addModuleDefs(
            "Variables",
            config.catsEq,
            config.catsShow,
            scalaJsReactReuse = false,
            circeEncoder = true
          )(
            parentBody
          )
        case Define(_, _, _) => // For now, we don't allow specifying Variables class parents.
          resolveVariables(schema, operation.variables).addToParentBody(
            config.catsEq,
            config.catsShow,
            config.monocleLenses,
            scalaJsReactReuse = false,
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
    schema:     Schema,
    algebra:    Query,
    subqueries: List[Term],
    fragments:  List[UntypedFragment],
    rootType:   Option[GType]
  ): CaseClass = {
    import Query._

    val fragmentsMap: Map[String, UntypedFragment] = fragments.map(f => f.name -> f).toMap

    def getType(typeName: String): NamedType =
      schema
        .definition(typeName)
        .getOrElse(
          abort(s"Undefined type [$typeName] in inline fragment").unsafeRunSync()
        )

    def go(
      currentAlgebra: Query,
      currentType:    Option[GType]
    ): ClassAccumulator =
      currentAlgebra match {
        case UntypedSelect(name, alias, _, _, UntypedSelect(fieldName, _, _, _, _))
            if fieldName.startsWith("subquery") =>
          val param = MetaTypes
            .get(name)
            .orElse(currentType.flatMap(_.field(name)))
            .fold(
              throw new Exception(
                s"Could not resolve type for field [$name] - Is this a valid field present in the schema?"
              )
            ) { nextType =>
              val i = fieldName.substring("subquery".length).toInt

              val subquery: Term.Ref = subqueries(i) match {
                case Term.Block((q: Term.Ref) :: Nil) => q
                case q: Term.Ref                      => q
                case other                            =>
                  throw new Exception(s"Unexpected subquery AST. Should be Term.Ref, was: [$other]")
              }
              ClassParam.fromGrackleType(
                name,
                nextType.dealias,
                isInput = false,
                alias = alias,
                typeOverride = Some(Type.Select(subquery, Type.Name("Data")))
              )
            }

          ClassAccumulator(parAccum = List(param))
        case UntypedSelect(name, alias, _, _, child)   =>
          val paramName = alias.getOrElse(name)

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
                  ClassParam.fromGrackleType(
                    paramName,
                    nextType.dealias,
                    isInput = false,
                    paramTypeNameOverride
                  )
                )
              )
            }
        case UntypedInlineFragment(typeName, _, child) =>
          // Single element in inline fragment
          go(child, typeName.map(getType).orElse(currentType))
        case UntypedFragmentSpread(name, _)            =>
          val fragment: UntypedFragment = fragmentsMap(name)
          go(fragment.child, getType(fragment.tpnme).some)
        case Group(selections)                         =>
          // A Group in an inline fragment "... on X" will be represented as Group(List(UntypedInlineFragment(X, ...), UntypedInlineFragment(X, ...))).
          // We fix that to UntypedInlineFragment(X, Group(List(..., ...)))
          val fixedSelections = selections.map(_ match {
            // We flatly assume that if the first element of a group is UntypedInlineFragment(X, ...), then all elements are.
            case Group(list @ UntypedInlineFragment(typeName, _, _) +: _) =>
              UntypedInlineFragment(
                typeName,
                List.empty,
                Group(list.collect { case UntypedInlineFragment(_, _, child) =>
                  child
                })
              )
            case other                                                    => other
          })

          // (Also, check what Grackle is returning when there's an interface)
          val hierarchyAccumulators =
            fixedSelections.zipWithIndex // We want to preserve order of appeareance
              .groupBy {
                _._1 match {
                  case UntypedInlineFragment(typeName, _, _) =>
                    typeName // Selection in inline fragment, group by discriminator
                  case _ =>
                    none // Selection in base group, group by none
                }
              }
              .toList
              .sortBy(_._2.head._2)      // Sort by first appeareance of each subtype
              .map { // Resolve groups
                case (Some(typeName), subQueries) =>
                  (typeName.some, // Unwrap inline fragment selections
                   subQueries.collect { case (UntypedInlineFragment(_, _, child), idx) =>
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
        case Empty                                     => ClassAccumulator()
        case _                                         =>
          throw new Exception(
            s"Unhandled Algebra: [$currentAlgebra] - Current Type: [$currentType]"
          )
      }

    val algebraTypes = go(algebra, rootType.flatMap(_.underlyingObject))

    CaseClass("Data", algebraTypes.parAccum, algebraTypes.classes)
  }

  protected def addData(
    schema:           Schema,
    operation:        UntypedOperation,
    config:           GraphQLGenConfig,
    subqueries:       List[Term],
    fragments:        List[UntypedFragment],
    rootTypeOverride: Option[NamedType] = None
  ): List[Stat] => List[Stat] =
    parentBody =>
      mustDefineType("Data")(parentBody) match {
        case Skip            =>
          addModuleDefs(
            "Data",
            config.catsEq,
            config.catsShow,
            config.scalaJsReactReuse,
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

          resolveData(
            schema,
            operation.query,
            subqueries,
            fragments,
            rootTypeOverride.orElse(rootType)
          )
            .addToParentBody(
              config.catsEq,
              config.catsShow,
              config.monocleLenses,
              config.scalaJsReactReuse,
              circeDecoder = true,
              forceModule = true
            )(parentBody)
      }

  private def isTermDefined(termName: String): List[Stat] => Boolean =
    parentBody =>
      parentBody.exists {
        // We are not checking in pattern assignments
        // case q"$_ val $tname: $_ = $_" => tname == tpe
        case Defn.Val(_, List(Pat.Var(Term.Name(name))), _, _)         => name == termName
        // case q"$_ var $tname: $_ = $_" => tname == tpe
        case Defn.Var.Initial(_, List(Pat.Var(Term.Name(name))), _, _) => name == termName
        case _                                                         => false
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
          case Defn.Object(_, Term.Name(name), Template.Initial(_, _, _, dataBody))
              if name == moduleName =>
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
    addValRefIntoModule(
      "varEncoder",
      "Variables",
      "jsonEncoderVariables",
      t"io.circe.Encoder.AsObject[Variables]"
    )

  protected val addDataDecoder: List[Stat] => List[Stat] =
    addValRefIntoModule("dataDecoder", "Data", "jsonDecoderData", t"io.circe.Decoder[Data]")

  protected def addConvenienceMethod(
    schemaType: Type,
    operation:  UntypedOperation,
    objName:    String
  ): List[Stat] => List[Stat] =
    parentBody =>
      parentBody
        .collectFirst {
          // case q"$_ class Variables $_(...$paramss) extends { ..$_ } with ..$_ { $_ => ..$_ }" =>
          case Defn.Class.Initial(
                _,
                Type.Name(name),
                _,
                Ctor.Primary.Initial(_, _, paramss),
                _
              ) if name == "Variables" =>
            // Strip "val" from mods.
            paramss
              .map(_.map {
                case Term.Param(_, name, decltpe, default) => param"$name: $decltpe = $default"
                case other                                 => throw new Exception(s"Unexpected param structure [$other]")
              })
              .toList
        }
        .map { paramss =>
          val variablesNames = paramss
            .map(_.map {
              case Term.Param(_, Name(name), _, _) => Term.Name(name)
              case other                           => throw new Exception(s"Unexpected param structure [$other]")
            })
            .toList
          val applied        =
            q"""def apply[F[_]]: clue.ClientAppliedF[F, $schemaType, ClientAppliedFP] =
                  new clue.ClientAppliedF[F, $schemaType, ClientAppliedFP] {
                    def applyP[P](client: clue.FetchClientWithPars[F, P, $schemaType]) = new ClientAppliedFP(client)
                  }"""
          parentBody ++
            (operation match {
              case _: UntypedQuery =>
                List(
                  applied,
                  q"""class ClientAppliedFP[F[_], P](val client: clue.FetchClientWithPars[F, P, $schemaType]) {
                      def query(...${(paramss.head :+ param"modParams: P => P = identity") +: paramss.tail}) =
                        client.request(${Term
                      .Name(objName)}).withInput(Variables(...$variablesNames), modParams)
                    }
                  """
                )

              case _: UntypedMutation     =>
                List(
                  applied,
                  q"""class ClientAppliedFP[F[_], P](val client: clue.FetchClientWithPars[F, P, $schemaType]) {
                      def execute(...${(paramss.head :+ param"modParams: P => P = identity") +: paramss.tail}) =
                        client.request(${Term
                      .Name(objName)}).withInput(Variables(...$variablesNames), modParams)
                    }
                  """
                )
              case _: UntypedSubscription =>
                // param"implicit client: clue.StreamingClient[F, $schemaType]"
                val clientParam = Term.Param(
                  mods = List(Mod.Implicit()),
                  name = Name("client"),
                  decltpe = t"clue.StreamingClient[F, $schemaType]".some,
                  default = none
                )
                List(
                  q"def subscribe[F[_]](...${paramss :+ List(clientParam)}) = client.subscribe(this).withInput(Variables(...$variablesNames))"
                )
            })
        }
        .getOrElse(parentBody)

}
