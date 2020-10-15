// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.macros

import cats.syntax.all._
import scala.annotation.StaticAnnotation
import scala.reflect.macros.blackbox
import scala.annotation.tailrec
import edu.gemini.grackle._
import edu.gemini.grackle.{ Type => GType }
import edu.gemini.grackle.{ NoType => GNoType }
import edu.gemini.grackle.{ TypeRef => GTypeRef }
import edu.gemini.grackle.UntypedOperation._
import cats.effect.IO
import cats.kernel.Monoid

class GraphQL(
  val mappings: Map[String, String] = Map.empty,
  val eq:       Boolean = false,
  val show:     Boolean = false,
  val lenses:   Boolean = false,
  val reuse:    Boolean = false,
  val debug:    Boolean = false
) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro GraphQLImpl.resolve
}
private[clue] final class GraphQLImpl(val c: blackbox.Context) extends GraphQLMacro {
  import c.universe._

  final def resolve(annottees: Tree*): Tree =
    macroResolve(annottees: _*)

  /**
   * Extract the `document` contents from the `GraphQLQuery` the marcro was applied to.
   */
  @tailrec
  private[this] def documentDef(tree: List[Tree]): Option[String] =
    tree match {
      case Nil                                          => none
      case q"$mods val document: $tpe = $document" :: _ =>
        scala.util.Try(c.eval(c.Expr[String](c.untypecheck(document.duplicate)))).toOption
      case _ :: tail                                    => documentDef(tail)
    }

  /**
   *  Holds the aggregated [[CaseClass]]es and their [[ClassParam]]s as we recurse the query AST.
   *
   * `parAccum` accumulates parameters until we have a whole case class definition.
   */
  private[this] case class ClassAccumulator(
    classes:  List[Class] = List.empty,
    parAccum: List[ClassParam] = List.empty,
    sum:      Option[Sum] = None
  )                                     {
    def withOverrideParams: ClassAccumulator =
      copy(parAccum = parAccum.map(_.copy(overrides = true)))
  }
  private[this] object ClassAccumulator {
    implicit val monoidClassAccumulator: Monoid[ClassAccumulator] = new Monoid[ClassAccumulator] {
      override def empty: ClassAccumulator = ClassAccumulator()
      override def combine(x: ClassAccumulator, y: ClassAccumulator): ClassAccumulator =
        ClassAccumulator(x.classes ++ y.classes, x.parAccum ++ y.parAccum)
    }
  }

  /**
   * Recurse the query AST and collect the necessary [[CaseClass]]es to hold its results.
   */
  private[this] def resolveData(
    schema:   Schema,
    algebra:  Query,
    rootType: GType,
    mappings: Map[String, String]
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
      currentType:    GType,
      nameOverride:   Option[String] = none
    ): ClassAccumulator =
      currentAlgebra match {
        case Select(name, args, child)      =>
          val paramName = nameOverride.getOrElse(name)
          val nextType  = Constants.MetaTypes.getOrElse(name, currentType.field(name))

          val accumulatorOpt =
            nextType.dealias match {
              case union: UnionType => go(child, union).some
              case _                =>
                nextType.underlyingObject match {
                  case GNoType  => none
                  case baseType => go(child, baseType).some
                }
            }

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
                                         mappings,
                                         paramTypeNameOverride
              )
            )
          )
        case UntypedNarrow(typeName, child) =>
          // Single element in inline fragment
          go(child, getType(typeName))
        case Rename(name, child)            =>
          go(child, currentType, name.some)
        case Group(selections)              =>
          // (Also, check what Grackle is returning when there's an interface)
          val hierarchyAccumulators =
            selections.zipWithIndex // We want to preserve order of appeareance
              .groupBy {
                _._1 match {
                  case UntypedNarrow(typeName, _) => typeName.some // Selection in inline fragment
                  case _                          => none          // Selection in base group
                }
              }
              .toList
              .sortBy(_._2.head._2) // Sort by first appeareance of each subtype
              .map { // Resolve groups
                case (Some(typeName), subQueries) =>
                  (typeName.some, // Unwrap inline fragment selections
                   subQueries.collect { case (UntypedNarrow(_, child), idx) =>
                     (go(child, getType(typeName)), idx)
                   }
                  )
                case (None, subQueries)           =>
                  (none, (subQueries.map { case (q, idx) => (go(q, currentType), idx) }))
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
              // More than one subtype. Needs discriminator.
              // Figure out discriminator name. Could be renamed.
              val discriminator = selections.collect {
                case Select(Constants.TypeSelect, _, _)               => Constants.TypeSelect
                case Rename(name, Select(Constants.TypeSelect, _, _)) => name
              } match {
                case field :: Nil => field
                case _            =>
                  abort(
                    s"Multiple inline fragments require (unique) selection of ${Constants.TypeSelect}"
                  ).unsafeRunSync()
              }

              val baseParams = baseAccumulator.parAccum.filterNot(_.name == discriminator)

              val subTypes = subTypeAccumulators.collect { case (typeName, accumulator) =>
                CaseClass(typeName,
                          accumulator.parAccum.filterNot(_.name == discriminator),
                          accumulator.classes
                )
              }

              ClassAccumulator(
                baseAccumulator.classes,
                baseParams,
                Sum(baseParams, baseAccumulator.classes, subTypes, discriminator).some
              )
          }

        case Empty => ClassAccumulator()
        case _     =>
          log(s"Unhandled Algebra: [$algebra]").unsafeRunSync()
          ClassAccumulator()
      }

    val algebraTypes = go(algebra, rootType.underlyingObject)

    CaseClass("Data", algebraTypes.parAccum, algebraTypes.classes)
  }

  // This might be useful in Grackle?
  private[this] def underlyingInputObject(tpe: GType): GType = {
    log(s"Searching for [$tpe] - [${tpe.getClass()}]")
    tpe match {
      case NullableType(tpe)  => underlyingInputObject(tpe)
      case ListType(tpe)      => underlyingInputObject(tpe)
      case _: GTypeRef        => underlyingInputObject(tpe.dealias)
      case o: InputObjectType => o
      case _                  => GNoType
    }
  }

  /**
   * Resolve the types of the operation's variable arguments.
   */
  private[this] def resolveVariables(
    schema:   Schema,
    vars:     List[Query.UntypedVarDef],
    mappings: Map[String, String]
  ): CaseClass = {
    val inputs = compileVarDefs(schema, vars)

    if (inputs.isLeft)
      abort(s"Error resolving operation input variables types [$vars]: [${inputs.left}]]")
    if (inputs.isBoth)
      log(s"Warning resolving operation input variables types [$vars]: [${inputs.left}]]")
    val inputValues = inputs.right.get

    CaseClass("Variables",
              inputValues.map(iv => ClassParam.fromGrackleType(iv.name, iv.tpe, mappings))
    )
  }

  private[this] def schemaImport(schemaType: Tree)(scope: String): List[Tree] =
    List(
      Import(
        Select(TypeNamesToTermNames.transform(schemaType), TermName(scope)),
        List(ImportSelector(termNames.WILDCARD, -1, null, -1))
      ),
      Apply(Ident(TermName(s"ignoreUnusedImport$scope")), List.empty)
    )

  private[this] def addImports(schemaType: Tree): List[Tree] => List[Tree] =
    parentBody => {
      val importDef = schemaImport(schemaType) _
      List(importDef("Scalars"), importDef("Enums"), importDef("Types")).flatten ++ parentBody
    }

  private[this] def addVars(
    schema:    Schema,
    operation: UntypedOperation,
    params:    GraphQLParams
  ): List[Tree] => List[Tree] =
    parentBody =>
      if (isTypeDefined("Variables")(parentBody))
        addModuleDefs("Variables", params.eq, params.show, reuse = false, encoder = true)(
          parentBody
        )
      else
        resolveVariables(schema, operation.variables, params.mappings).addToParentBody(
          params.eq,
          params.show,
          params.lenses,
          reuse = false,
          encoder = true,
          forceModule = true
        )(parentBody)

  private[this] def addData(
    schema:    Schema,
    operation: UntypedOperation,
    params:    GraphQLParams
  ): List[Tree] => List[Tree] =
    parentBody =>
      if (isTypeDefined("Data")(parentBody))
        addModuleDefs("Data", params.eq, params.show, params.reuse, decoder = true)(parentBody)
      else {
        // For some reason, schema.schemaType only returns the Query type.
        val schemaType = schema.definition("Schema").getOrElse(schema.defaultSchemaType)

        // Leaving this comment in order to reproduce the issue.
        // log(schema.schemaType.asInstanceOf[ObjectType].fields).unsafeRunSync()
        // log(schema.definition("Schema").getOrElse(schema.defaultSchemaType)
        //     .asInstanceOf[ObjectType].fields).unsafeRunSync()

        val rootType = operation match {
          // This is how things should look like.
          // case _: UntypedQuery        => schema.queryType
          // case _: UntypedMutation     => schema.mutationType.getOrElse(GNoType)
          // case _: UntypedSubscription => schema.subscriptionType.getOrElse(GNoType)
          case _: UntypedQuery        => schemaType.field("query").asNamed.get
          case _: UntypedMutation     => schemaType.field("mutation").asNamed.getOrElse(GNoType)
          case _: UntypedSubscription => schemaType.field("subscription").asNamed.getOrElse(GNoType)
        }

        resolveData(schema, operation.query, rootType, params.mappings).addToParentBody(
          params.eq,
          params.show,
          params.lenses,
          params.reuse,
          decoder = true,
          forceModule = true
        )(parentBody)
      }

  private[this] def addValRefIntoModule(
    valName:       String,
    moduleName:    String,
    moduleValName: String,
    tpe:           Tree
  ): List[Tree] => List[
    Tree
  ] =
    parentBody =>
      parentBody
        .collectFirst {
          case q"$_ object $tname extends { ..$_ } with ..$_ { $_ => ..$dataBody }"
              if tname == TermName(moduleName) =>
            dataBody
        }
        .filter(isTermDefined(moduleValName))
        .map(_ =>
          addValDef(valName, tpe, Select(Ident(TermName(moduleName)), TermName(moduleValName)))(
            parentBody
          )
        )
        .getOrElse(parentBody)

  private[this] val addVarEncoder: List[Tree] => List[Tree] =
    addValRefIntoModule("varEncoder",
                        "Variables",
                        "jsonEncoderVariables",
                        tq"io.circe.Encoder[Variables]"
    )

  private[this] val addDataDecoder: List[Tree] => List[Tree] =
    addValRefIntoModule("dataDecoder", "Data", "jsonDecoderData", tq"io.circe.Decoder[Data]")

  private[this] def addConvenienceMethod(
    schemaType: Tree,
    operation:  UntypedOperation
  ): List[Tree] => List[Tree] =
    parentBody =>
      parentBody
        .collectFirst {
          case q"$_ class Variables $_(...$paramss) extends { ..$_ } with ..$_ { $_ => ..$_ }" =>
            paramss
        }
        .map { paramss =>
          val variablesNames = paramss.map(_.map { case q"$_ val $name: $_ = $_" => name })
          parentBody :+
            (operation match {
              case _: UntypedQuery        =>
                q"""
                        def query[F[_]](...$paramss)(implicit client: _root_.clue.GraphQLClient[F, $schemaType]) =
                          client.request(this)(Variables(...$variablesNames))
                      """
              case _: UntypedMutation     =>
                q"""
                        def execute[F[_]](...$paramss)(implicit client: _root_.clue.GraphQLClient[F, $schemaType]) =
                          client.request(this)(Variables(...$variablesNames))
                      """
              case _: UntypedSubscription =>
                q"""
                        def subscribe[F[_]](...$paramss)(implicit client: _root_.clue.GraphQLStreamingClient[F, $schemaType]) =
                          client.subscribe(this)(Variables(...$variablesNames))
                      """
            })
        }
        .getOrElse(parentBody)

  // We cannot fully resolve types since we cannot evaluate in the current annotated context.
  // Therefore, the schema name is always treated as unqualified, and therefore must be unique
  // across the whole compilation unit.
  // See: https://stackoverflow.com/questions/19379436/cant-access-parents-members-while-dealing-with-macro-annotations
  private[this] def schemaType(list: List[Tree]): Option[Tree] =
    list.collect {
      case tq"GraphQLOperation[$schema]"      => schema
      case tq"clue.GraphQLOperation[$schema]" => schema
    }.headOption

  /**
   * Actual macro application, generating case classes to hold the query results and its variables.
   */
  protected[this] def expand(annottees: Tree*): IO[Tree] =
    annottees match {
      case List(
            q"$objMods object $objName extends { ..$objEarlyDefs } with ..$objParents { $objSelf => ..$objDefs }"
          ) =>
        schemaType(objParents) match {
          case None =>
            abort(
              "Invalid annotation target: must be an object extending GraphQLOperation[Schema]"
            )

          case Some(schemaType) =>
            documentDef(objDefs) match {
              case None =>
                abort(
                  "The GraphQLOperation must define a 'val document: String' that can be evaluated at compile time."
                )

              case Some(document) =>
                // Get macro settings passed thru -Xmacro-settings.
                val settings       = MacroSettings.fromCtxSettings(c.settings)
                // Get annotation parameters.
                val optionalParams = buildOptionalParams[GraphQLOptionalParams]
                val params         = optionalParams.resolve(settings)

                // Parse schema and metadata.
                unqualifiedType(schemaType) match {
                  case None =>
                    abort(s"Could not extract unqualified type from schema type [$schemaType]")

                  case Some(schemaTypeName) =>
                    retrieveSchema(settings.schemaDirs, schemaTypeName).flatMap { schema =>
                      // Parse the operation.
                      val queryResult = QueryParser.parseText(document)
                      if (queryResult.isLeft)
                        abort(
                          s"Could not parse document: ${queryResult.left.get.toChain.map(_.toString).toList.mkString("\n")}"
                        )
                      else {
                        IO.whenA(queryResult.isBoth)(
                          log(
                            s"Warning parsing document: ${queryResult.left.get.toChain.map(_.toString).toList.mkString("\n")}"
                          )
                        ) >> IO {
                          val operation = queryResult.toOption.get

                          // Modifications to add the missing definitions.
                          val modObjDefs = scala.Function.chain(
                            List(
                              addImports(schemaType),
                              addVars(schema, operation, params),
                              addData(schema, operation, params),
                              addVarEncoder,
                              addDataDecoder,
                              addConvenienceMethod(schemaType, operation)
                            )
                          )

                          // Congratulations! You got a full-fledged GraphQLOperation (hopefully).
                          q"""
                            $objMods object $objName extends { ..$objEarlyDefs } with ..$objParents { $objSelf =>
                              ..${modObjDefs(objDefs)}
                            }
                          """
                        }.flatTap(result => IO.whenA(params.debug)(log(result)))
                      }
                    }
                }
            }

          case _ =>
            abort(
              "Invalid annotation target: must be an object extending GraphQLOperation[Schema]"
            )
        }
    }
}
