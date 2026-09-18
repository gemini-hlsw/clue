// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import grackle.Ast

/**
 * Pure helpers used by the `gql` macro to assemble and inspect GraphQL documents parsed by
 * grackle's parser. Kept macro-free (no `scala.quoted` dependency) so they can be unit-tested
 * directly, without `compileErrors`.
 */
private[clue] object GraphQLDocuments {

  // A spliced subquery always stands where a selection set goes, so replacing each splice with a
  // placeholder selection keeps the document parseable without knowing what's actually spliced in.
  // The splice's index is embedded as the field's alias so a later pass (`spliceScopes`,
  // `spliceSites`) can find which definition encloses which splice; `__typename` keeps it a legal
  // field in any selection-set position.
  private def placeholderSelection(i: Int): String = s"{ clue_splice_$i: __typename }"

  // `wrapVariableDefs` never has splices, so it keeps an unindexed, ordinary placeholder.
  private val TypenamePlaceholder = "{ __typename }"

  // The alias `placeholderSelection` embeds, read back by `spliceIndexOf`.
  private val SpliceAlias = "clue_splice_(\\d+)".r

  /** Unescape `$$` -> `$`, as in any interpolated string. */
  def unescapeDollar(part: String): String = part.replace("$$", "$")

  /**
   * The placeholder document text: `$$` unescaped in each literal part, joined with an indexed
   * placeholder selection set (`{ clue_splice_N: __typename }`, N the 0-based splice index) in
   * place of each splice.
   */
  def placeholderDocument(parts: List[String]): String =
    parts.map(unescapeDollar) match {
      case Nil          => ""
      case head :: tail =>
        head + tail.zipWithIndex.map { case (part, i) =>
          s" ${placeholderSelection(i)} $part"
        }.mkString
    }

  /**
   * Wrap a parenthesized `VariableDefs` string (e.g. `"($ep: Episode!)"`) as a parseable operation.
   */
  def wrapVariableDefs(defs: String): String = s"query $defs $TypenamePlaceholder"

  /**
   * The variables declared by every operation in `doc`, keyed by name (without the `$`), flattened
   * into one map (a later operation's declaration for a repeated name wins). A shorthand query, or
   * a document with only fragments, declares none. Fragments before the operation don't affect
   * this.
   *
   * A document may contain more than one operation (clue's own clients select one by
   * `operationName`, see `core/src/main/scala/clue/clients.scala`), so this flattened view is too
   * coarse to check a splice against — use `spliceScopes` for that. `headerVariables` remains
   * useful for a single-operation document (as `wrapVariableDefs` always produces), and as the
   * defensive fallback for a splice `spliceScopes` couldn't place.
   */
  def headerVariables(doc: Ast.Document): Map[String, Ast.Type] =
    doc
      .collect { case op: Ast.OperationDefinition.Operation => op }
      .flatMap(_.variables)
      .map(v => v.name.value -> v.tpe)
      .toMap

  // Splice index of a placeholder field (`{ clue_splice_N: __typename }`), if `f` is one.
  private def spliceIndexOf(f: Ast.Selection.Field): Option[Int] =
    (f.name.value, f.alias.map(_.value)) match {
      case ("__typename", Some(SpliceAlias(n))) => Some(n.toInt)
      case _                                    => None
    }

  // Every placeholder field's splice index found in `selections`, recursing into plain fields and
  // inline fragments; a fragment spread is opaque here (its target's own definition is walked
  // separately, as a top-level `FragmentDefinition`).
  private def spliceIndices(selections: List[Ast.Selection]): List[Int] =
    selections.flatMap {
      case f: Ast.Selection.Field          =>
        spliceIndexOf(f) match {
          case Some(i) => List(i)
          case None    => spliceIndices(f.selectionSet)
        }
      case i: Ast.Selection.InlineFragment => spliceIndices(i.selectionSet)
      case _: Ast.Selection.FragmentSpread => Nil
    }

  // The variables every operation in `doc` declares, as one map per operation.
  private def operationVariableMaps(doc: Ast.Document): List[Map[String, Ast.Type]] =
    doc.collect { case op: Ast.OperationDefinition.Operation =>
      op.variables.map(v => v.name.value -> v.tpe).toMap
    }

  // The variables safe to assume in scope inside a fragment definition: those every operation in
  // `doc` declares, with the same type. A fragment must be valid wherever it's spread, so only a
  // variable every operation declares identically can be assumed available; with a single
  // operation, that intersection is exactly that operation's variables (today's single-operation
  // behaviour). With no operations at all (e.g. a subquery's own document, or a fragment-only
  // document), there is nothing to intersect, so nothing is in scope.
  private def fragmentScope(doc: Ast.Document): Map[String, Ast.Type] =
    operationVariableMaps(doc) match {
      case Nil          => Map.empty
      case head :: tail =>
        tail.foldLeft(head)((scope, vars) =>
          scope.filter { case (k, v) => vars.get(k).contains(v) }
        )
    }

  /**
   * The GraphQL variables in scope for each splice in `doc`, keyed by splice index (as embedded by
   * `placeholderDocument`). A splice inside an operation's own selection set sees that operation's
   * own declared variables only — so in `query A(...) $Sub query B { ... }`, `$Sub`'s requirements
   * are checked against `A`'s declarations, not `B`'s. A splice inside a fragment definition sees
   * `fragmentScope`, the intersection of every operation's variables.
   *
   * A splice that lands in neither (shouldn't happen — every splice sits inside some operation's or
   * fragment's selection set) is simply absent from the result; the caller falls back to
   * `headerVariables` for any index missing here.
   */
  def spliceScopes(doc: Ast.Document): Map[Int, Map[String, Ast.Type]] = {
    val fragScope = fragmentScope(doc)

    def collect(
      selections: List[Ast.Selection],
      scope:      Map[String, Ast.Type]
    ): Map[Int, Map[String, Ast.Type]] =
      selections.foldLeft(Map.empty[Int, Map[String, Ast.Type]]) { (acc, sel) =>
        sel match {
          case f: Ast.Selection.Field          =>
            spliceIndexOf(f) match {
              case Some(i) => acc + (i -> scope)
              case None    => acc ++ collect(f.selectionSet, scope)
            }
          case i: Ast.Selection.InlineFragment => acc ++ collect(i.selectionSet, scope)
          case _: Ast.Selection.FragmentSpread => acc
        }
      }

    doc.foldLeft(Map.empty[Int, Map[String, Ast.Type]]) { (acc, defn) =>
      val found = defn match {
        case op: Ast.OperationDefinition.Operation      =>
          collect(op.selectionSet, op.variables.map(v => v.name.value -> v.tpe).toMap)
        case qs: Ast.OperationDefinition.QueryShorthand =>
          collect(qs.selectionSet, Map.empty)
        case fd: Ast.FragmentDefinition                 =>
          collect(fd.selectionSet, fragScope)
        case _                                          => Map.empty[Int, Map[String, Ast.Type]]
      }
      acc ++ found
    }
  }

  /**
   * A human-readable label for the definition enclosing each splice in `doc`, keyed by splice
   * index: `operation [Name]` for a named operation, `operation` for an unnamed one (including a
   * shorthand query), `fragment [Name]` for a splice inside a fragment definition. Used to name the
   * "declaration site" in the macro's caller-check errors.
   */
  def spliceSites(doc: Ast.Document): Map[Int, String] =
    doc.flatMap {
      case op: Ast.OperationDefinition.Operation      =>
        val label = op.name.fold("operation")(n => s"operation [${n.value}]")
        spliceIndices(op.selectionSet).map(_ -> label)
      case qs: Ast.OperationDefinition.QueryShorthand =>
        spliceIndices(qs.selectionSet).map(_ -> "operation")
      case fd: Ast.FragmentDefinition                 =>
        spliceIndices(fd.selectionSet).map(_ -> s"fragment [${fd.name.value}]")
      case _                                          => Nil
    }.toMap

  /**
   * GraphQL "is variable usage allowed": `declared` must be usable where `required` is expected,
   * recursively per https://spec.graphql.org/October2021/#IsVariableUsageAllowed
   * (`AreTypesCompatible`).
   *
   * The spec's extra relaxation — a nullable variable is allowed at a non-null location when either
   * side has a non-null default value — is deliberately not implemented here, since neither side is
   * available to this check. That only makes this check stricter than the spec, never looser.
   */
  def usableAs(declared: Ast.Type, required: Ast.Type): Boolean =
    (declared, required) match {
      case (Ast.Type.NonNull(d), Ast.Type.NonNull(r))    => usableAs(d.merge, r.merge)
      case (Ast.Type.NonNull(d), r)                      => usableAs(d.merge, r)
      case (_, Ast.Type.NonNull(_))                      => false
      case (Ast.Type.List(d), Ast.Type.List(r))          => usableAs(d, r)
      case (Ast.Type.List(_), _) | (_, Ast.Type.List(_)) => false
      case (d, r)                                        => d == r
    }
}
