// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.gen

import cats.data.State
import cats.syntax.all.*
import grackle.Query.UntypedFragment
import grackle.UntypedOperation.*
import grackle.Value.ListValue
import grackle.Value.ObjectValue
import grackle.{Term as _, Type as GType, *}

import scala.meta.*

trait QueryGen extends Generator {

  // TODO This could be more sophisticated.
  // Matches both `GraphQLOperation[S]` and `GraphQLOperation.Typed[S, ...]`, extracting `S`.
  protected def extractSchemaType(list: List[Init]): Option[Type.Name] =
    list.collectFirst {
      case Init.Initial(
            Type.Apply.Initial(Type.Name("GraphQLOperation"), (tpe @ Type.Name(_)) :: _),
            _,
            _
          ) =>
        tpe
      case Init.Initial(
            Type.Apply.Initial(
              Type.Select(Term.Name("GraphQLOperation"), Type.Name("Typed")),
              (tpe @ Type.Name(_)) :: _
            ),
            _,
            _
          ) =>
        tpe
    }

  // The GraphQL root type(s) declared via `@GraphQLType("A", "B", ...)`. Empty if the annotation is
  // absent. Non-literal arguments are ignored.
  protected def extractRootTypes(mods: List[Mod]): List[String] =
    GraphQLTypeAnnotation
      .unapply(mods)
      .toList
      .flatten
      .collect { case Lit.String(rootType) => rootType }

  // Matches `GraphQLSubquery[S]` / `GraphQLSubquery.Typed[S, ...]`, extracting `S`. Used to
  // recognize subqueries (their root type comes from the `@GraphQLType` annotation, not here).
  protected def extractSubquerySchemaType(list: List[Init]): Option[Type.Name] =
    list.collectFirst {
      case Init.Initial(
            Type.Apply.Initial(Type.Name("GraphQLSubquery"), (tpe @ Type.Name(_)) :: _),
            _,
            _
          ) =>
        tpe
      case Init.Initial(
            Type.Apply.Initial(
              Type.Select(Term.Name("GraphQLSubquery"), Type.Name("Typed")),
              (tpe @ Type.Name(_)) :: _
            ),
            _,
            _
          ) =>
        tpe
    }

  case class InterpolatedGql(parts: List[GqlPart]) {
    def render: String = parts
      .traverse {
        case GqlPart.Literal(value) => State.pure[Int, String](value)
        case _                      =>
          // Render each spliced subquery as an aliased `__typename` selection. The alias carries
          // the index (so codegen can map it back to `subqueries(i)`), while `__typename` is a
          // valid selection on any object/interface/union, so the host document also typechecks
          // against the schema. The spliced subquery itself is validated at its definition site.
          State.inspect[Int, String](i => s"{ subquery$i: __typename }") <* State.modify(_ + 1)
      }
      .runA(0)
      .value
      .mkString

    def subqueries: List[Term] =
      parts.collect { case GqlPart.Subquery(term) =>
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

  // The declared GraphQL variables of a subquery, from its `type VariableDefs = "(...)"` member
  // (parenthesized var-defs in operation-header syntax). Absent ⇒ the subquery references no
  // variables. Uses the type test + field accessors to avoid the deprecated `Defn.Type` extractor.
  protected def extractVariableDefs(stats: List[Stat]): Option[String] =
    stats.collectFirst { case d: Defn.Type if d.name.value == "VariableDefs" => d.body }.collect {
      case Lit.String(value) => value
    }

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

  /**
   * Resolve the types of the operation's variable definitions.
   *
   * Assumes the operation has already been validated (see [[validateParsed]]), so variable type
   * resolution is known to succeed; any problems were already reported as diagnostics.
   */
  def computeVarDefs(
    schema: Schema,
    vars:   List[Query.UntypedVarDef]
  ): List[InputValue] =
    new QueryCompiler(GQLParser, schema, List.empty).compileVarDefs(vars).toOption.get

  // A `SelectElaborator` that performs grackle's standard schema typechecking and, additionally,
  // emits a warning whenever a selected field is `@deprecated`. This is how deprecation surfaces
  // as a scalafix diagnostic (instead of being printed to stderr during generation).
  private val deprecationWarningElaborator: QueryCompiler.SelectElaborator =
    QueryCompiler.SelectElaborator { case (parentType, fieldName, _) =>
      parentType
        .fieldInfo(fieldName)
        .flatMap(field => Deprecation.fromDirectives(field.directives))
        .fold(QueryCompiler.Elab.unit) { deprecation =>
          QueryCompiler.Elab.warning(
            s"Field [$fieldName] in [${parentType.name}] is deprecated (${deprecation.reason})"
          )
        }
    }

  /**
   * Validate already-parsed `operations` (and `fragments`) against the `schema`, rooting each
   * operation's selection set at the type returned by `rootTypeOf`.
   *
   * This mirrors grackle's `QueryCompiler.compile` pipeline (variable/fragment checks, field
   * mergeability, then per-operation elaboration via [[deprecationWarningElaborator]]), with two
   * adaptations needed to validate without executing:
   *   - elaboration runs with *dummy* variable values (see [[createDummyVars]]), so argument
   *     elaboration can resolve variable references even though no runtime values are available;
   *   - the root type is supplied explicitly, so this also works for subqueries (selection sets on
   *     an arbitrary type), which `compile` can't root directly.
   *
   * All errors and warnings are accumulated into the [[Result]] (nothing is thrown).
   */
  private def validateParsed(
    schema:     Schema,
    rootTypeOf: UntypedOperation => Result[NamedType],
    operations: List[UntypedOperation],
    fragments:  List[UntypedFragment]
  ): Result[Unit] = {
    val compiler = new QueryCompiler(GQLParser, schema, List.empty)
    val phases   =
      QueryCompiler.IntrospectionElaborator(QueryCompiler.IntrospectionLevel.Full).toList ++
        List(
          QueryCompiler.VariablesSkipAndFragmentElaborator,
          QueryCompiler.MergeFields,
          deprecationWarningElaborator
        )
    val fragMap  = fragments.map(f => f.name -> f).toMap

    for {
      // `reportUnused = false`: grackle's unused detection is unreliable — its `collectValueRefs`
      // overwrites instead of accumulating variable refs (`loop(values, Set(nme))`), so when one
      // value holds several variables (e.g. an input object with multiple `$var` fields) all but the
      // last are falsely reported as unused. Note that re-enabling it would also need an exemption
      // for subqueries: a subquery legitimately declares variables that only a subquery it splices
      // uses, and a splice is rendered as `__typename` here (see [[InterpolatedGql.render]]).
      // TODO Re-enable unused detection when grackle releases the bug fix for `collectValueRefs`.
      _ <- Result.fromProblems(
             compiler.validateVariablesAndFragments(operations, fragments, reportUnused = false)
           )
      _ <- Result.fromProblems(compiler.validateFieldMergeability(operations, fragments))
      _ <- operations.traverse_ { op =>
             for {
               rootType <- rootTypeOf(op)
               varDefs  <- compiler.compileVarDefs(op.variables)
               _        <- phases
                             .foldLeftM(op.query)((acc, phase) =>
                               phase.transformFragments *> phase.transform(acc)
                             )
                             .runA(
                               QueryCompiler.ElabState(
                                 None,
                                 schema,
                                 Context(rootType),
                                 createDummyVars(varDefs),
                                 fragMap,
                                 op.query,
                                 Env.empty,
                                 List.empty,
                                 QueryCompiler.Elab.pure(_: Query)
                               )
                             )
             } yield ()
           }
    } yield ()
  }

  /**
   * Validate the operation `document` against the `schema`, accumulating all problems. This is the
   * validation entry point for hand-written operations, where the code generator is not used.
   */
  protected def validateDocument(schema: Schema, document: String): Result[Unit] =
    GQLParser.parseText(document).flatMap { case (operations, fragments) =>
      validateParsed(schema, _.rootTpe(schema), operations, fragments)
    }

  /**
   * Infers types for the variables a query references, from where they are used, pairing each with
   * a dummy value. A subquery is validated in isolation, so it declares none of the variables it
   * references (they belong to the enclosing operation); supplying inferred dummy values lets the
   * subquery still be validated (fields, arguments, deprecation, ...) without spuriously reporting
   * those variables as undefined.
   */
  private def inferVariableVars(
    schema:    Schema,
    rootType:  GType,
    query:     Query,
    fragments: List[UntypedFragment]
  ): Query.Vars = {
    val fragMap: Map[String, UntypedFragment] = fragments.map(f => f.name -> f).toMap

    def fromValue(value: Value, tpe: GType): Query.Vars =
      value match {
        case Value.VariableRef(name) => Map(name -> ((tpe, createDummyValue(tpe))))
        case ObjectValue(fields)     =>
          tpe.dealias match {
            case NullableType(inner)           => fromValue(value, inner)
            case InputObjectType(_, _, ivs, _) =>
              fields.foldLeft(Map.empty[String, (GType, Value)]) { case (acc, (fname, fvalue)) =>
                ivs.find(_.name == fname).fold(acc)(iv => acc ++ fromValue(fvalue, iv.tpe))
              }
            case _                             => Map.empty
          }
        case ListValue(elems)        =>
          tpe.dealias match {
            case NullableType(inner) => fromValue(value, inner)
            case ListType(elem)      =>
              elems.foldLeft(Map.empty[String, (GType, Value)])((acc, e) =>
                acc ++ fromValue(e, elem)
              )
            case _                   => Map.empty
          }
        case _                       => Map.empty
      }

    // `@skip`/`@include` take a single boolean `if` argument.
    def fromDirectives(directives: List[Directive]): Query.Vars =
      directives
        .filter(d => d.name == "skip" || d.name == "include")
        .foldLeft(Map.empty[String, (GType, Value)]) { (acc, d) =>
          d.args.foldLeft(acc) {
            case (a, Query.Binding("if", Value.VariableRef(name))) =>
              a + (name -> ((ScalarType.BooleanType, Value.BooleanValue(true))))
            case (a, _)                                            => a
          }
        }

    def go(q: Query, currentType: Option[GType]): Query.Vars =
      q match {
        case Query.UntypedSelect(name, _, args, directives, child) =>
          val fieldArgs: List[InputValue] =
            currentType.flatMap(_.fieldInfo(name)).map(_.args).getOrElse(Nil)
          val argVars: Query.Vars         =
            args.foldLeft(Map.empty[String, (GType, Value)]) { (acc, b) =>
              fieldArgs.find(_.name == b.name).fold(acc)(iv => acc ++ fromValue(b.value, iv.tpe))
            }
          val next: Option[GType]         = currentType.flatMap(_.field(name)).flatMap(_.underlyingObject)
          argVars ++ fromDirectives(directives) ++ go(child, next)
        case Query.UntypedInlineFragment(tpnme, directives, child) =>
          fromDirectives(directives) ++ go(child,
                                           tpnme.flatMap(schema.definition).orElse(currentType)
          )
        case Query.UntypedFragmentSpread(name, directives)         =>
          fromDirectives(directives) ++
            fragMap
              .get(name)
              .fold(Map.empty[String, (GType, Value)])(f => go(f.child, schema.definition(f.tpnme)))
        case Query.Group(queries)                                  =>
          queries.foldLeft(Map.empty[String, (GType, Value)])((acc, c) => acc ++ go(c, currentType))
        case _                                                     =>
          Map.empty
      }

    go(query, rootType.some)
  }

  // Render a grackle type back to GraphQL type syntax (e.g. `Episode!`, `[String!]`, `ID`). grackle
  // types are non-null by default; `NullableType` marks the nullable positions.
  private def renderGraphQLType(tpe: GType): String =
    tpe match {
      case NullableType(inner) => renderNullableGraphQLType(inner)
      case ListType(inner)     => s"[${renderGraphQLType(inner)}]!"
      case named: NamedType    => s"${named.name}!"
      case other               => throw new Exception(s"Cannot render GraphQL type [$other]")
    }

  private def renderNullableGraphQLType(tpe: GType): String =
    tpe match {
      case ListType(inner)  => s"[${renderGraphQLType(inner)}]"
      case named: NamedType => named.name
      case other            => throw new Exception(s"Cannot render GraphQL type [$other]")
    }

  /**
   * Infer the GraphQL variables a subquery references, from their usage, rendered as a
   * parenthesized var-def list (`($a: T, $b: U)`) in operation-header syntax, sorted by name for
   * deterministic output. Empty if the subquery references none. Used to auto-emit
   * `type VariableDefs` for `@GraphQL` subqueries that don't declare it explicitly.
   */
  protected def inferSubqueryVariableDefs(
    schema:       Schema,
    rootTypeName: String,
    subquery:     String
  ): String =
    scala.util
      .Try {
        (for {
          rootType <- schema.definition(rootTypeName)
          parsed   <- GQLParser.parseText(s"query $subquery").toOption
        } yield {
          val (operations, fragments) = parsed
          val inferred: Query.Vars    =
            operations.foldLeft(Map.empty[String, (GType, Value)])((acc, op) =>
              acc ++ inferVariableVars(schema, rootType, op.query, fragments)
            )
          if (inferred.isEmpty) ""
          else
            inferred.toList
              .sortBy(_._1)
              .map { case (name, (tpe, _)) => s"$$$name: ${renderGraphQLType(tpe)}" }
              .mkString("(", ", ", ")")
        }).getOrElse("")
      }
      .getOrElse("")

  /**
   * The variables to validate a subquery against: the declared `type VariableDefs`, or — when the
   * generator processes the subquery (`@GraphQL`), which also emits the declaration for it — the
   * set inferred from usage. A hand-written subquery must declare them itself, so `infer` is false
   * there and an undeclared variable is reported.
   */
  protected def subqueryVariableDefs(
    schema:        Schema,
    rootTypeNames: List[String],
    stats:         List[Stat],
    subquery:      String,
    infer:         Boolean
  ): String =
    extractVariableDefs(stats).getOrElse {
      rootTypeNames match {
        // Inference needs a single unambiguous root type, which is what `@GraphQL` requires anyway.
        case rootTypeName :: Nil if infer =>
          inferSubqueryVariableDefs(schema, rootTypeName, subquery)
        case _                            => ""
      }
    }

  // Emit `type VariableDefs = "(...)"` into a generated subquery object when its variables were
  // inferred (the author didn't declare them) and it references at least one. When the author wrote
  // an explicit `type VariableDefs`, it is already in the body and carried through, so nothing is
  // added.
  protected def addVariableDefsTypeAlias(
    explicitVariableDefs: Option[String],
    variableDefs:         String
  ): List[Stat] => List[Stat] =
    parentBody =>
      if (explicitVariableDefs.isDefined || variableDefs.isEmpty) parentBody
      else q"type VariableDefs = ${Lit.String(variableDefs)}" :: parentBody

  /**
   * Validate a subquery (a selection set on `rootTypeName`) against the `schema`. Grackle's
   * `compile` always roots a document at the schema's operation types, so the root type is supplied
   * explicitly here.
   *
   * `variableDefs` is the subquery's declared variables (from its `type VariableDefs` member),
   * parenthesized in operation-header syntax (e.g. `($ep: Episode!)`), or empty if it declares
   * none. They are spliced into the wrapper operation so the standard variable checks apply: a
   * variable used but not declared is reported, and each declared type is checked against its
   * usage.
   */
  protected def validateSubquery(
    schema:       Schema,
    rootTypeName: String,
    variableDefs: String,
    subquery:     String
  ): Result[Unit] =
    Result
      .fromOption(
        schema.definition(rootTypeName),
        s"Undefined root type [$rootTypeName] for subquery"
      )
      .flatMap { rootType =>
        GQLParser.parseText(s"query $variableDefs $subquery").flatMap {
          case (operations, fragments) =>
            validateParsed(schema, _ => Result.success(rootType), operations, fragments)
        }
      }

  /**
   * Validate a subquery against each of `rootTypeNames`, accumulating all problems (grackle's
   * messages already name the offending type). The selection must be valid against every type.
   */
  protected def validateSubqueryTypes(
    schema:        Schema,
    rootTypeNames: List[String],
    variableDefs:  String,
    subquery:      String
  ): Result[Unit] =
    rootTypeNames.parTraverse_(validateSubquery(schema, _, variableDefs, subquery))

  import DefineType._
  protected def addVars(
    inputs: List[InputValue],
    config: GraphQLGenConfig
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
          CaseClass(
            "Variables",
            inputs.map(iv => ClassParam.fromGrackleType(iv.name, iv.tpe, isInput = true))
          ).addToParentBody(
            config.catsEq,
            config.catsShow,
            config.monocleLenses,
            scalaJsReactReuse = false,
            circeEncoder = true,
            forceModule = true
          )(parentBody)
      }

  // `@include(if:)` and `@skip(if:)` make a field conditionally present in the response,
  // so such a field must be generated as optional even if it's non-nullable in the schema.
  private def hasConditionalDirective(directives: List[Directive]): Boolean =
    directives.exists(d => d.name == "include" || d.name == "skip")

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
      new ClassAccumulator(
        classAccumulator.classes,
        parAccum = classAccumulator.parAccum.map(_.copy(overrides = true)),
        classAccumulator.sum
      )
  }

  private def createDummyValue(tpe: GType, parentIsOneOf: Boolean = false): Value =
    tpe.dealias match {
      case NullableType(tpe) if parentIsOneOf   => createDummyValue(tpe)
      case NullableType(_)                      => Value.AbsentValue
      case ScalarType.IntType                   => Value.IntValue(0)
      case ScalarType.FloatType                 => Value.FloatValue(0.0)
      case ScalarType.StringType                => Value.StringValue("")
      case ScalarType.BooleanType               => Value.BooleanValue(true)
      case ScalarType.IDType                    => Value.IDValue("")
      case ScalarType(_, _, _)                  => Value.IntValue(0)
      case EnumType(_, _, values, _)            => Value.EnumValue(values.head.name)
      case ListType(tpe0)                       => ListValue(List(createDummyValue(tpe0)))
      case i @ InputObjectType(_, _, fields, _) =>
        // For oneOf input objects, only set one field.
        val fieldsHandlingOneOf = if (i.isOneOf) fields.headOption.toList else fields
        ObjectValue(fieldsHandlingOneOf.map(iv => iv.name -> createDummyValue(iv.tpe, i.isOneOf)))
      case t @ TypeRef(_, _)                    => createDummyValue(t.dealias)
      case _                                    => Value.AbsentValue
    }

  private def createDummyVars(inputs: List[InputValue]): Query.Vars =
    inputs.map(iv => iv.name -> (iv.tpe, createDummyValue(iv.tpe))).toMap

  /**
   * Recurse the query AST and collect the necessary [[CaseClass]]es to hold its results.
   *
   * This assumes the query has already been validated against the schema (see [[validateParsed]]),
   * which the caller runs first. As a result it does not re-check field existence or arguments:
   * lookups that "can't fail" on a valid query are treated as defensive internal errors.
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
          // Unreachable: validateParsed already verified inline-fragment/fragment type conditions.
          throw new Exception(s"Undefined type [$typeName] in validated inline fragment")
        )

    def go(
      currentAlgebra: Query,
      currentType:    Option[GType]
    ): ClassAccumulator =
      currentAlgebra match {
        case UntypedSelect(name, alias, _, directives, UntypedSelect(_, Some(subAlias), _, _, _))
            if subAlias.startsWith("subquery") =>
          val isConditional: Boolean = hasConditionalDirective(directives)

          val param = MetaTypes
            .get(name)
            .orElse(currentType.flatMap(_.field(name)))
            .fold(
              // Unreachable: validateParsed already verified this field exists on the schema.
              throw new Exception(s"Could not resolve type for validated field [$name]")
            ) { nextType =>
              val i = subAlias.substring("subquery".length).toInt

              val subquery: Term.Ref = subqueries(i) match {
                case Term.Block((q: Term.Ref) :: Nil) => q
                case q: Term.Ref                      => q
                case other                            =>
                  throw new Exception(s"Unexpected subquery AST. Should be Term.Ref, was: [$other]")
              }
              ClassParam.fromGrackleType(
                alias.getOrElse(name),
                nextType.dealias,
                isInput = false,
                alias = alias,
                typeOverride = Some(Type.Select(subquery, Type.Name("Data"))),
                forceOptional = isConditional
              )
            }

          ClassAccumulator(parAccum = List(param))
        case UntypedSelect(name, alias, _, directives, child) =>
          val paramName: String = alias.getOrElse(name)

          // A field with an @include or @skip directive may be absent from the response, so it
          // must be generated as an Option (unless it's already optional in the schema).
          val isConditional: Boolean = hasConditionalDirective(directives)

          MetaTypes
            .get(name)
            .orElse(currentType.flatMap(_.field(name)))
            .fold(
              // Unreachable: validateParsed already verified this field exists on the schema.
              throw new Exception(s"Could not resolve type for validated field [$name]")
            ) { nextType =>
              // Deprecation is reported as a warning during validation (see validateParsed); here
              // we only need it to annotate the generated field.
              val deprecation: Option[Deprecation] =
                currentType
                  .flatMap(_.fieldInfo(name))
                  .flatMap(info => Deprecation.fromDirectives(info.directives))

              val accumulatorOpt: Option[ClassAccumulator] =
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
                    paramTypeNameOverride,
                    deprecation = deprecation,
                    forceOptional = isConditional
                  )
                )
              )
            }
        case UntypedInlineFragment(typeName, _, child)        =>
          // Single element in inline fragment
          go(child, typeName.map(getType).orElse(currentType))
        case UntypedFragmentSpread(name, _)                   =>
          val fragment: UntypedFragment = fragmentsMap(name)
          go(fragment.child, getType(fragment.tpnme).some)
        case Group(selections)                                =>
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
          val hierarchyAccumulators
            : List[(Option[String], List[(Accumulator[Class, ClassParam, Sum], Int)])] =
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
              ClassAccumulator(
                baseAccumulator.classes ++ singleSubType._2.classes,
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
        case Empty                                            => ClassAccumulator()
        case _                                                =>
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
          val schemaType: NamedType =
            schema.definition("Schema").getOrElse(schema.defaultSchemaType)

          // Leaving this comment in order to reproduce the issue.
          // log(schema.schemaType.asInstanceOf[ObjectType].fields).unsafeRunSync()
          // log(schema.definition("Schema").getOrElse(schema.defaultSchemaType)
          //     .asInstanceOf[ObjectType].fields).unsafeRunSync()

          val rootType: Option[NamedType] = operation match {
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
    objName:    String,
    config:     GraphQLGenConfig
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
          // When descriptor generation is enabled, tag the request/subscription with the
          // object name so otel4s can name the span `clue-<op>-<ObjectName>`.
          val objTerm        = Term.Name(objName)
          val afterRequest   =
            if (config.descriptor)
              q"client.request($objTerm).withDescriptor(${Lit.String(objName)})"
            else
              q"client.request($objTerm)"
          val afterSubscribe =
            if (config.descriptor)
              q"client.subscribe(this).withDescriptor(${Lit.String(objName)})"
            else
              q"client.subscribe(this)"
          parentBody ++
            (operation match {
              case _: UntypedQuery =>
                List(
                  applied,
                  q"""class ClientAppliedFP[F[_], P](val client: clue.FetchClientWithPars[F, P, $schemaType]) {
                      def query(...${(paramss.head :+ param"modParams: P => P = identity") +: paramss.tail}) =
                        $afterRequest.withInput(Variables(...$variablesNames), modParams)
                    }
                  """
                )

              case _: UntypedMutation     =>
                List(
                  applied,
                  q"""class ClientAppliedFP[F[_], P](val client: clue.FetchClientWithPars[F, P, $schemaType]) {
                      def execute(...${(paramss.head :+ param"modParams: P => P = identity") +: paramss.tail}) =
                        $afterRequest.withInput(Variables(...$variablesNames), modParams)
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
                  q"def subscribe[F[_]](...${paramss :+ List(clientParam)}) = $afterSubscribe.withInput(Variables(...$variablesNames))"
                )
            })
        }
        .getOrElse(parentBody)

}
