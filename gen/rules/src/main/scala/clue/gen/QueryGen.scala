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
               // A selection with fragments on subtypes must select `__typename` next to them, so
               // the generated decoder (see Generator.addModuleDefs) can tell subtypes apart.
               _        <- Result.fromProblems(
                             validateTypenameSelections(schema, fragMap, rootType, op.query)
                           )
               // A spliced subquery (rendered as `{ subqueryN: __typename }`, see
               // InterpolatedGql.render) is only recognized by `resolveData` as the whole
               // selection set of a field; anywhere else it silently fails to splice.
               _        <- Result.fromProblems(validateSubqueryPlacement(fragMap, op.query))
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

  // The immediate children of a selection set, as a list. grackle's parser collapses a
  // single-element selection set to a bare node instead of wrapping it in a `Group` (so
  // `{ a }` parses as a bare `UntypedSelect`, not `Group(List(UntypedSelect(...)))`); this undoes
  // that collapse uniformly so callers can always treat a selection set as a `List[Query]`.
  private def selectionsOf(q: Query): List[Query] =
    q match {
      case Query.Group(qs) => qs
      case Query.Empty     => List.empty
      case other           => List(other)
    }

  /**
   * Classify the immediate items of a selection set (whose current GraphQL type is `currentType`)
   * into base-level selects and variant inline fragments, recursively expanding nested `Group`s and
   * normalizing named fragment spreads into inline fragments along the way.
   *
   * A fragment (inline or, once normalized, a spread) is "same-type" when its type condition is
   * absent, `currentType` is unknown, or `currentType <:< ` its type condition (grackle's
   * `Type.<:<`: same type, an interface `currentType` implements, or a union containing it) — such
   * a fragment is just grouping (e.g. for `@include`), so its children are unwrapped directly into
   * the result. Anything else is a "variant" fragment (a proper subtype of `currentType`): it is
   * kept as one flat item per occurrence, tagged with its type condition; merging same-typed
   * variants together (accumulating their children, in order) is left to the caller, which already
   * needs a stable `groupBy` for other reasons.
   *
   * Same-type fragments are unwrapped by recursing into this same function (not into the caller's
   * own walk), so a bare fragment routed here by the caller can't loop forever. `conditional`
   * accumulates whether any same-type fragment/spread unwrapped along the way (including this
   * call's own ancestors) carried `@skip`/`@include`; a same-type fragment's own directives are
   * folded in before recursing into its children, so a `__typename` (or anything else) that
   * flattens to base-level through such a fragment is marked conditional even though it looks
   * unconditional once flattened — see `validateTypenameSelections`, which requires the
   * discriminator to be selected unconditionally.
   */
  private def flattenSelections(
    schema:       Schema,
    fragmentsMap: Map[String, UntypedFragment],
    selections:   List[Query],
    currentType:  Option[GType],
    conditional:  Boolean = false
  ): List[FlatSelection] = {
    def isSameType(tpnme: Option[String]): Boolean =
      tpnme.forall(t => currentType.forall(ct => schema.definition(t).forall(td => ct <:< td)))

    selections.flatMap {
      case Query.Group(inner)                                                         =>
        flattenSelections(schema, fragmentsMap, inner, currentType, conditional)
      case Query.UntypedFragmentSpread(name, directives)                              =>
        fragmentsMap.get(name).toList.flatMap { frag =>
          flattenSelections(
            schema,
            fragmentsMap,
            List(Query.UntypedInlineFragment(frag.tpnme.some, directives, frag.child)),
            currentType,
            conditional
          )
        }
      case Query.UntypedInlineFragment(tpnme, directives, child) if isSameType(tpnme) =>
        flattenSelections(
          schema,
          fragmentsMap,
          selectionsOf(child),
          currentType,
          conditional || hasConditionalDirective(directives)
        )
      case Query.UntypedInlineFragment(tpnme, _, child)                               =>
        List(FlatSelection(tpnme, child, conditional))
      case other                                                                      =>
        List(FlatSelection(none, other, conditional))
    }
  }

  /**
   * Per-operation check that every selection set with at least one variant fragment (see
   * [[flattenSelections]]) also selects `__typename` at the base level (possibly aliased)
   * unconditionally, so `resolveData`'s generated decoder (see `Generator.addModuleDefs`,
   * `TypeType.Sum`) can tell which subtype a response is. A `__typename` guarded by `@skip`/
   * `@include` — directly, or via an enclosing same-type fragment/spread carrying the directive
   * (see `FlatSelection.conditional`) — may be absent from the response even though the field
   * itself isn't, so it is rejected just like a missing `__typename`. Also, when two or more
   * variant types share a concrete type (`__typename` in an actual response is always concrete,
   * e.g. an interface variant covering a type also covered by another variant), a response of that
   * concrete type could only ever decode to one of them, silently losing the other's fields, so
   * that combination is rejected too. Mirrors [[inferVariableVars]]'s walk through
   * fields/fragments, tracking the current type as it goes.
   */
  private def validateTypenameSelections(
    schema:    Schema,
    fragments: Map[String, UntypedFragment],
    rootType:  GType,
    query:     Query
  ): List[Problem] = {
    def check(selections: List[Query], currentType: Option[GType]): List[Problem] = {
      val flat: List[FlatSelection] =
        flattenSelections(schema, fragments, selections, currentType)

      val variantTypeNames: List[String] = flat.collect { case FlatSelection(Some(t), _, _) =>
        t
      }.distinct

      // Whether each base-level `__typename` select is conditional: directly guarded by
      // `@skip`/`@include`, or unwrapped from a same-type fragment/spread that was.
      val typenames: List[Boolean] = flat.collect {
        case FlatSelection(None,
                           Query.UntypedSelect(TypeSelect, _, _, directives, _),
                           conditional
            ) =>
          conditional || hasConditionalDirective(directives)
      }

      val ctName: String = currentType.flatMap(_.asNamed).fold("?")(_.name)

      val typenameProblems: List[Problem] =
        if (variantTypeNames.isEmpty) List.empty
        else if (typenames.isEmpty) {
          List(
            Problem(
              s"Selection on [$ctName] has fragments on subtypes [${variantTypeNames
                  .mkString(", ")}] but does not select `__typename`. Add `__typename` next to " +
                "them so the response can be decoded to the right subtype."
            )
          )
        } else if (typenames.forall(identity)) {
          List(
            Problem(
              s"Selection on [$ctName] has fragments on subtypes [${variantTypeNames
                  .mkString(", ")}] but its `__typename` is selected with `@skip`/`@include` " +
                "(directly or via an enclosing fragment). The discriminator must be selected " +
                "unconditionally so every response can be decoded to the right subtype."
            )
          )
        } else List.empty

      // Two variant types that share a concrete type would make that concrete type's response
      // decode to whichever variant appears first, silently losing the other's fields (see
      // `resolveData`'s `discriminatorCases`). Only concrete types the current type can actually
      // return count: two interface fragments may overlap on an object outside the current union,
      // which can never show up here. Report each overlapping pair once, in order of appearance.
      val overlapProblems: List[Problem] =
        if (variantTypeNames.sizeIs < 2) List.empty
        else {
          val reachable: Set[String] =
            currentType.flatMap(_.asNamed).fold(Set.empty[String])(concreteTypeNames(schema, _))

          val concreteByVariant: List[(String, Set[String])] =
            variantTypeNames.map(t =>
              t -> schema
                .definition(t)
                .fold(Set.empty[String])(concreteTypeNames(schema, _).intersect(reachable))
            )

          def pairs[A](xs: List[A]): List[(A, A)] =
            xs match {
              case Nil       => List.empty
              case x :: rest => rest.map(x -> _) ++ pairs(rest)
            }

          pairs(concreteByVariant).flatMap { case ((a, aTypes), (b, bTypes)) =>
            val shared = aTypes.intersect(bTypes)
            Option.when(shared.nonEmpty)(
              Problem(
                s"Selection on [$ctName] has fragments on [$a] and [$b] that overlap on " +
                  s"concrete type(s) [${shared.toList.sorted
                      .mkString(", ")}]: a response of that type can only decode to one of " +
                  "them, so the other's fields would be lost. Merge them into a single " +
                  "fragment (e.g. on the concrete type)."
              )
            )
          }
        }

      val here: List[Problem] = typenameProblems ++ overlapProblems

      val nested: List[Problem] = flat.flatMap {
        case FlatSelection(None, Query.UntypedSelect(name, _, _, _, child), _) =>
          val next =
            MetaTypes
              .get(name)
              .orElse(currentType.flatMap(_.field(name)))
              .flatMap(_.underlyingObject)
          check(selectionsOf(child), next)
        case FlatSelection(Some(t), child, _)                                  =>
          check(selectionsOf(child), schema.definition(t))
        case _                                                                 =>
          List.empty
      }

      here ++ nested
    }

    check(selectionsOf(query), rootType.some)
  }

  // A subquery is spliced by rendering it as `{ subqueryN: __typename }` (see
  // `InterpolatedGql.render`); `resolveData` only recognizes it in that shape when it is the
  // *whole* selection set of a field (`UntypedSelect(_, _, _, _, UntypedSelect(_, Some(alias), ...))`
  // with `alias` starting with "subquery"). Anywhere else — inside a fragment (inline or spread),
  // or alongside sibling selections in a `Group` — it silently fails to splice, so report it.
  private val subqueryPlacementMessage: String =
    "A subquery can only be spliced as the whole selection set of a field (`field $Subquery`), " +
      "not inside a fragment or next to other selections. Splice it on a field of the " +
      "subquery's type, or move the fragment into the subquery."

  private def validateSubqueryPlacement(
    fragments: Map[String, UntypedFragment],
    query:     Query
  ): List[Problem] = {
    def isSubqueryMarker(q: Query): Boolean =
      q match {
        case Query.UntypedSelect(TypeSelect, Some(alias), _, _, _) => alias.startsWith("subquery")
        case _                                                     => false
      }

    // `wholeChild` is true exactly when `q` is the entire (unwrapped) selection set of a field;
    // that's the only legal position for a subquery marker.
    def check(q: Query, wholeChild: Boolean): List[Problem] =
      if (isSubqueryMarker(q))
        if (wholeChild) List.empty else List(Problem(subqueryPlacementMessage))
      else
        q match {
          case Query.UntypedSelect(_, _, _, _, child)   =>
            check(child, wholeChild = true)
          case Query.UntypedInlineFragment(_, _, child) =>
            check(child, wholeChild = false)
          case Query.UntypedFragmentSpread(name, _)     =>
            fragments.get(name).fold(List.empty[Problem])(f => check(f.child, wholeChild = false))
          case Query.Group(items)                       =>
            items.flatMap(check(_, wholeChild = false))
          case _                                        =>
            List.empty
        }

    check(query, wholeChild = false)
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

  // The concrete (object) type names covered by `t`: itself if it's already concrete, every
  // implementor if it's an interface, or the (dealiased, recursively resolved) concrete members if
  // it's a union.
  private def concreteTypeNames(schema: Schema, t: NamedType): Set[String] =
    t.dealias match {
      case o: ObjectType    => Set(o.name)
      case i: InterfaceType =>
        schema.types
          .map(_.dealias)
          .collect { case o: ObjectType if o <:< i => o.name }
          .toSet
      case u: UnionType     => u.members.flatMap(m => concreteTypeNames(schema, m)).toSet
      case other            => Set(other.name)
    }

  // `tpe` without a top-level `Option` wrapper, if any.
  private def stripOption(tpe: Type): Type =
    tpe match {
      case Type.Apply.Initial(Type.Name("Option"), List(inner)) => inner
      case other                                                => other
    }

  private def isOptional(tpe: Type): Boolean =
    tpe match {
      case Type.Apply.Initial(Type.Name("Option"), _) => true
      case _                                          => false
    }

  // Whether two `ClassParam`s are the same GraphQL selection, so GraphQL would merge them into a
  // single response value. Ignores:
  //  - `overrides`: only says whether the param came from the base level.
  //  - `deprecation`: schema metadata, not part of the response shape (an interface field may be
  //    deprecated while an object's implementation of it is not, or vice versa).
  //  - a top-level `Option`: added by `@include`/`@skip` (`forceOptional`), or by an object type
  //    narrowing a nullable interface field to non-null; the selection is the same either way.
  // Plain case-class `==` doesn't work here: `tpe` is a scalameta `Type`, and scalameta trees
  // compare by identity, not structure, so two independently-built trees with the same shape (e.g.
  // two `Type.Name("String")`) are never `==`. `.structure` is scalameta's structural-equality
  // string, so it's used instead.
  private def sameSelection(a: ClassParam, b: ClassParam): Boolean =
    a.name == b.name && stripOption(a.tpe).structure == stripOption(b.tpe).structure

  // Structural equality for `Class`/`Sum`, for the same reason as [[sameSelection]]: their
  // `ClassParam`s (and, transitively, nested classes) embed scalameta `Type`s, so plain `==`
  // can't be used to tell two independently-built but identical classes apart.
  //
  // Comparison is by response name, not position: GraphQL selection sets are unordered for field
  // merging purposes, so `friends { id name }` at one level and `friends { name id }` at another
  // are the same selection even though their `ClassParam`/`Class` lists are built in different
  // orders. It's also deprecation-insensitive (see [[sameParams]]).
  private def sameClass(a: Class, b: Class): Boolean =
    (a, b) match {
      case (CaseClass(n1, p1, nested1), CaseClass(n2, p2, nested2)) =>
        n1 == n2 && sameParams(p1, p2) && sameClasses(nested1, nested2)
      case (SumClass(n1, s1, o1), SumClass(n2, s2, o2))             =>
        n1 == n2 && o1 == o2 && sameSum(s1, s2)
      case _                                                        =>
        false
    }

  // Same size, and every param in `a` has a same-named param in `b` with the same type and
  // `overrides`. Names are unique within a selection set, so a name lookup is enough. Ignores
  // `deprecation`: schema metadata, not part of the response shape (an interface field may be
  // deprecated while an object's implementation of it is not, or vice versa).
  private def sameParams(a: List[ClassParam], b: List[ClassParam]): Boolean = {
    val byName: Map[String, ClassParam] = b.map(p => p.name -> p).toMap
    a.sizeIs == b.size && a.forall { x =>
      byName.get(x.name).exists { y =>
        x.tpe.structure == y.tpe.structure && x.overrides == y.overrides
      }
    }
  }

  // Same size, and every class in `a` has a same-named class in `b` for which [[sameClass]] holds.
  private def sameClasses(a: List[Class], b: List[Class]): Boolean = {
    val byName: Map[String, Class] = b.map(c => c.name -> c).toMap
    a.sizeIs == b.size && a.forall { x =>
      byName.get(x.name).exists(y => sameClass(x, y))
    }
  }

  private def sameSum(a: Sum, b: Sum): Boolean =
    sameParams(a.params, b.params) && sameClasses(a.nested, b.nested) &&
      sameClasses(a.instances, b.instances) && a.discriminator == b.discriminator

  // The single param that a group of [[sameSelection]]-equal params collapses to.
  //  - Type: a base-level (override) occurrence wins, since the trait it overrides is typed by the
  //    base level; otherwise a non-optional one, since an unconditional occurrence means the
  //    field is always present, however many conditional ones there are besides.
  //  - `override` if any occurrence was base-level; deprecated if any occurrence was.
  private def mergeGroup(group: List[ClassParam]): ClassParam = {
    val representative: ClassParam =
      group
        .find(_.overrides)
        .orElse(group.find(p => !isOptional(p.tpe)))
        .getOrElse(group.head)
    representative.copy(
      overrides = group.exists(_.overrides),
      deprecation = group.flatMap(_.deprecation).headOption
    )
  }

  // Merge params that GraphQL itself merges: the same response name selected more than once (e.g.
  // at the base level and again inside a fragment). A group of [[sameSelection]] params whose
  // nested classes (if the field is an object) are all identical collapses to one param (see
  // [[mergeGroup]]) and one nested class. Same name with different content (different type, or
  // different sub-selections) is left alone: it surfaces as a compile error in the generated code,
  // as before.
  //
  // `inherited` are the nested classes of the enclosing base level, when `acc` is a variant of a
  // sum: they are emitted once in the sum's companion and referenced from every variant, so a
  // variant's own identical copy is dropped (it would otherwise shadow the base one and give the
  // `override val` a different type than the trait's).
  private def mergeDuplicateSelections(
    acc:       ClassAccumulator,
    inherited: List[Class] = List.empty
  ): ClassAccumulator = {
    val order: List[String]                     = acc.parAccum.map(_.name).distinct
    val byName: Map[String, List[ClassParam]]   = acc.parAccum.groupBy(_.name)
    val classesByName: Map[String, List[Class]] = acc.classes.groupBy(_.name)

    val (mergedParAccum, mergedClasses): (List[List[ClassParam]], List[List[Class]]) =
      order.map { name =>
        val group: List[ClassParam]   = byName(name)
        val own: List[Class]          = classesByName.getOrElse(name, List.empty)
        val inheritedOwn: List[Class] = inherited.filter(_.name == name)
        val nested: List[Class]       = inheritedOwn ++ own

        val mergeable: Boolean =
          group.tail.forall(sameSelection(_, group.head)) &&
            nested.drop(1).forall(sameClass(_, nested.head))

        if (mergeable)
          (List(mergeGroup(group)), if (inheritedOwn.nonEmpty) List.empty else own.take(1))
        else
          (group, own)
      }.unzip

    acc.copy(classes = mergedClasses.flatten, parAccum = mergedParAccum.flatten)
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
  ): Class = {
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
        case UntypedSelect(
              name,
              alias,
              _,
              directives,
              UntypedSelect(TypeSelect, Some(subAlias), _, _, _)
            ) if subAlias.startsWith("subquery") =>
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
        case UntypedInlineFragment(_, _, _)                   =>
          // grackle's parser collapses a selection set with a single inline fragment to a bare
          // node instead of wrapping it in a `Group`, so route it through the same
          // flatten-and-classify logic as the `Group` case below (it may itself be a variant).
          go(Group(List(currentAlgebra)), currentType)
        case UntypedFragmentSpread(_, _)                      =>
          // Same reasoning as the bare-inline-fragment case above.
          go(Group(List(currentAlgebra)), currentType)
        case Group(selections)                                =>
          // Normalize named-fragment spreads to inline fragments and unwrap same-type fragments
          // (grouping only, e.g. for `@include`) into a flat, ordered list of base-level selects
          // and variant inline fragments (fragments on a proper subtype of `currentType`). See
          // `flattenSelections` for the classification rule.
          val flatSelections: List[FlatSelection] =
            flattenSelections(schema, fragmentsMap, selections, currentType)

          val hierarchyAccumulators
            : List[(Option[String], List[(Accumulator[Class, ClassParam, Sum], Int)])] =
            flatSelections.zipWithIndex // We want to preserve order of appeareance
              .groupBy(_._1.variant)    // Group by discriminator (None = base group)
              .toList
              .sortBy(_._2.head._2)     // Sort by first appeareance of each subtype
              .map { // Resolve groups
                case (Some(typeName), items) =>
                  (typeName.some,
                   items.map { case (FlatSelection(_, child, _), idx) =>
                     (go(child, getType(typeName).some), idx)
                   }
                  )
                case (None, items)           =>
                  (none,
                   items.map { case (FlatSelection(_, q, _), idx) => (go(q, currentType), idx) }
                  )
              }

          val baseAccumulators    = hierarchyAccumulators.collectFirst { case (None, accumulators) =>
            accumulators
          }.orEmpty
          val baseAccumulator     = mergeDuplicateSelections(baseAccumulators.map(_._1).combineAll)
          val subTypeAccumulators = hierarchyAccumulators.collect {
            case (Some(typeName), accumulators) =>
              (typeName,
               mergeDuplicateSelections(
                 ClassAccumulator(
                   accumulators.map(_._1).combineAll.classes,
                   (baseAccumulators.map { case (accumlator, idx) =>
                     (accumlator.withOverrideParams, idx)
                   } ++ accumulators).sortBy(_._2).map(_._1).combineAll.parAccum
                 ),
                 inherited = baseAccumulator.classes
               )
              )
          }

          subTypeAccumulators match {
            case Nil      => baseAccumulator // No variants.
            case variants =>
              // Every inline fragment / named-fragment spread on a proper subtype of
              // `currentType` is a variant (no special single-variant case): build a sum with one
              // instance per variant, discriminated on `__typename`. Validation (see
              // `validateParsed`/`validateTypenameSelections`) already guarantees a base-level
              // `__typename` select whenever there is at least one variant.
              val (discriminatorKey, discriminatorIsAliased): (String, Boolean) =
                flatSelections
                  .collectFirst {
                    case FlatSelection(None, UntypedSelect(TypeSelect, alias, _, _, _), _) =>
                      (alias.getOrElse(TypeSelect), alias.isDefined)
                  }
                  .getOrElse(
                    // Unreachable: validateTypenameSelections rejects variants without a base
                    // `__typename` select before we ever get here.
                    throw new Exception("Missing `__typename` for a validated variant selection")
                  )

              // A bare `__typename` carries no information beyond which case class the response
              // decodes to, so it's noise: it is consumed by the discriminating decoder (see
              // Generator.addModuleDefs) and stripped out of every params list we emit (dropped
              // from the trait, every instance, and its lens). An aliased `__typename` (e.g.
              // `kind: __typename`) is instead an explicit request for the value as a field: it is
              // kept as the decoder key AND generated as a regular field, which lets users retain
              // the raw type name (e.g. for logging, or for the subtypes folded into `Other`).
              def stripDiscriminator(params: List[ClassParam]): List[ClassParam] =
                if (discriminatorIsAliased) params
                else params.filterNot(_.name == discriminatorKey)

              val ct: NamedType =
                currentType
                  .flatMap(_.asNamed)
                  .getOrElse(
                    // Unreachable: a variant fragment is only classified relative to a known type.
                    throw new Exception("Variant fragment without a known current type")
                  )

              val reachableConcreteTypes: Set[String] = concreteTypeNames(schema, ct)

              // Only the concrete types `ct` can actually return: a variant on an interface may
              // also cover objects outside `ct` (e.g. outside the current union), which never
              // show up here.
              def coveredBy(typeName: String): Set[String] =
                concreteTypeNames(schema, getType(typeName)).intersect(reachableConcreteTypes)

              val coveredConcreteTypes: Set[String] =
                variants.flatMap { case (typeName, _) => coveredBy(typeName) }.toSet
              val missingConcreteTypes: Set[String] =
                reachableConcreteTypes -- coveredConcreteTypes

              // `__typename` in a response is always a *concrete* object type, so a variant on an
              // interface/union type condition (e.g. `... on Pilot { ... }`) must be matched by
              // every concrete type it covers, not by its own (non-concrete) name. Build the
              // explicit `(concrete type name, instance name)` mapping the decoder needs: one
              // entry per concrete name, in variant order. Overlap between variants (two variants
              // covering the same concrete type) is rejected by `validateTypenameSelections`
              // before we ever get here, so each concrete name maps to exactly one variant.
              val discriminatorCases: List[(String, String)] = {
                val seen = scala.collection.mutable.LinkedHashMap.empty[String, String]
                variants.foreach { case (typeName, _) =>
                  coveredBy(typeName).toList.sorted.foreach(concreteName =>
                    seen.getOrElseUpdate(concreteName, typeName)
                  )
                }
                seen.toList
              }

              if (missingConcreteTypes.nonEmpty && variants.exists(_._1 == "Other"))
                // Unreachable in practice: no schema in the wild names a concrete type `Other`.
                throw new Exception(
                  "Cannot add a fallback `Other` instance: a variant is already named `Other`."
                )

              val subTypes: List[CaseClass]        = variants.map { case (typeName, accumulator) =>
                CaseClass(typeName, stripDiscriminator(accumulator.parAccum), accumulator.classes)
              }
              // Not every concrete type is covered by a variant: add a fallback instance so the
              // decoder (and the sealed trait) still account for every possible response.
              val otherInstance: Option[CaseClass] =
                Option.when(missingConcreteTypes.nonEmpty)(
                  CaseClass("Other",
                            stripDiscriminator(baseAccumulator.withOverrideParams.parAccum)
                  )
                )

              val baseParams: List[ClassParam] = stripDiscriminator(baseAccumulator.parAccum)

              ClassAccumulator(
                baseAccumulator.classes,
                baseParams,
                Sum(
                  baseParams,
                  baseAccumulator.classes,
                  subTypes ++ otherInstance.toList,
                  SumDiscriminator(discriminatorKey,
                                   discriminatorCases,
                                   otherInstance.map(_ => "Other")
                  ).some
                ).some
              )
          }
        case Empty                                            => ClassAccumulator()
        case _                                                =>
          throw new Exception(
            s"Unhandled Algebra: [$currentAlgebra] - Current Type: [$currentType]"
          )
      }

    val algebraTypes = go(algebra, rootType.flatMap(_.underlyingObject))

    // The root of a query is normally a plain object type (Query/Mutation/Subscription), but a
    // subquery (`@GraphQLType`) can root directly at an interface/union, in which case its own
    // "Data" is itself the sum (rather than a field somewhere inside it wrapping one).
    algebraTypes.sum.fold[Class](CaseClass("Data", algebraTypes.parAccum, algebraTypes.classes))(
      sum => SumClass("Data", sum)
    )
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

// One base-level item of a flattened selection set (see `QueryGen.flattenSelections`). `variant` is
// the type condition for a fragment on a proper subtype, `None` for a base-level select.
// `conditional` is true when an enclosing same-type fragment/spread that was unwrapped carried
// `@skip`/`@include`, so the item may be absent from the response even though it looks unconditional
// here. Top-level (not nested in the trait) so pattern matches on it need no outer-reference check.
private[gen] final case class FlatSelection(
  variant:     Option[String],
  query:       Query,
  conditional: Boolean
)
