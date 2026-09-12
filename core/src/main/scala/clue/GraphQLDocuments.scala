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

  // A spliced subquery always stands where a selection set goes, so replacing each splice with
  // this placeholder selection keeps the document parseable without knowing what's actually
  // spliced in.
  private val TypenamePlaceholder = "{ __typename }"

  /** Unescape `$$` -> `$`, as in any interpolated string. */
  def unescapeDollar(part: String): String = part.replace("$$", "$")

  /**
   * The placeholder document text: `$$` unescaped in each literal part, joined with a placeholder
   * selection set in place of each splice.
   */
  def placeholderDocument(parts: List[String]): String =
    parts.map(unescapeDollar).mkString(s" $TypenamePlaceholder ")

  /** Wrap a parenthesized `VariableDefs` string (e.g. `"($ep: Episode!)"`) as a parseable operation. */
  def wrapVariableDefs(defs: String): String = s"query $defs $TypenamePlaceholder"

  /**
   * The variables declared by every operation in `doc`, keyed by name (without the `$`). A
   * shorthand query, or a document with only fragments, declares none. Fragments before the
   * operation don't affect this.
   */
  def headerVariables(doc: Ast.Document): Map[String, Ast.Type] =
    doc
      .collect { case op: Ast.OperationDefinition.Operation => op }
      .flatMap(_.variables)
      .map(v => v.name.value -> v.tpe)
      .toMap

  /**
   * GraphQL "is variable usage allowed": `declared` must be usable where `required` is expected.
   * Same base type once a top-level `NonNull` is stripped from both, and a non-null requirement
   * needs a non-null declaration.
   */
  def usableAs(declared: Ast.Type, required: Ast.Type): Boolean = {
    def stripNonNull(tpe: Ast.Type): Ast.Type = tpe match {
      case Ast.Type.NonNull(of) => of.merge
      case other                => other
    }

    stripNonNull(declared) == stripNonNull(required) &&
    (!required.isInstanceOf[Ast.Type.NonNull] || declared.isInstanceOf[Ast.Type.NonNull])
  }
}
