// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import grackle.Ast
import grackle.GraphQLParser

class GraphQLDocumentsSuite extends munit.FunSuite {

  private val parser: GraphQLParser =
    GraphQLParser(GraphQLParser.defaultConfig.copy(terseError = false))

  private def parseDoc(text: String): Ast.Document =
    parser.parseText(text).toOption.getOrElse(fail(s"failed to parse: $text"))

  // The type of a single variable `$x`, declared with GraphQL type text `tpeText`.
  private def tpe(tpeText: String): Ast.Type =
    GraphQLDocuments
      .headerVariables(parseDoc(s"query ($$x: $tpeText) { __typename }"))
      .getOrElse("x", fail(s"no variable parsed for type [$tpeText]"))

  // placeholderDocument

  test("placeholderDocument with no splices is the single part, unchanged") {
    assertEquals(GraphQLDocuments.placeholderDocument(List("query { hero }")), "query { hero }")
  }

  test("placeholderDocument with one splice joins with an indexed placeholder selection") {
    assertEquals(
      GraphQLDocuments.placeholderDocument(List("query (", ") { hero }")),
      "query ( { clue_splice_0: __typename } ) { hero }"
    )
  }

  test("placeholderDocument with two splices joins with an indexed placeholder selection each") {
    assertEquals(
      GraphQLDocuments.placeholderDocument(List("a", "b", "c")),
      "a { clue_splice_0: __typename } b { clue_splice_1: __typename } c"
    )
  }

  test("placeholderDocument unescapes $$ in every part") {
    assertEquals(
      GraphQLDocuments.placeholderDocument(List("has $$ep", "trailing $$id")),
      "has $ep { clue_splice_0: __typename } trailing $id"
    )
  }

  // headerVariables

  test("headerVariables of a shorthand query declares none") {
    assertEquals(GraphQLDocuments.headerVariables(parseDoc("{ hero }")),
                 Map.empty[String, Ast.Type]
    )
  }

  test("headerVariables of a query (...) header") {
    val vars = GraphQLDocuments.headerVariables(parseDoc("query ($ep: Episode!) { hero }"))
    assertEquals(vars.keySet, Set("ep"))
    assertEquals(vars("ep").name, "Episode!")
  }

  test("headerVariables of a named query Foo(...) header") {
    val vars = GraphQLDocuments.headerVariables(parseDoc("query Foo($ep: Episode!) { hero }"))
    assertEquals(vars.keySet, Set("ep"))
    assertEquals(vars("ep").name, "Episode!")
  }

  test("headerVariables sees the operation's declared variables in a fragment-first document") {
    val vars = GraphQLDocuments.headerVariables(
      parseDoc("fragment f on Character { name } query ($ep: Episode!) { hero }")
    )
    assertEquals(vars.keySet, Set("ep"))
    assertEquals(vars("ep").name, "Episode!")
  }

  // usableAs

  test("usableAs: Episode! declared, Episode! required") {
    assert(GraphQLDocuments.usableAs(tpe("Episode!"), tpe("Episode!")))
  }

  test("usableAs: Episode! declared, Episode required (non-null satisfies nullable)") {
    assert(GraphQLDocuments.usableAs(tpe("Episode!"), tpe("Episode")))
  }

  test("usableAs: Episode declared, Episode! required is false (nullable can't satisfy non-null)") {
    assert(!GraphQLDocuments.usableAs(tpe("Episode"), tpe("Episode!")))
  }

  test("usableAs: different names is false") {
    assert(!GraphQLDocuments.usableAs(tpe("Episode!"), tpe("Character!")))
  }

  test("usableAs: [ID!]! declared, [ID!] required is true") {
    assert(GraphQLDocuments.usableAs(tpe("[ID!]!"), tpe("[ID!]")))
  }

  test("usableAs: [ID] declared, [ID!] required is false") {
    assert(!GraphQLDocuments.usableAs(tpe("[ID]"), tpe("[ID!]")))
  }

  test("usableAs: [ID!]! declared, [ID]! required is true (recurses through NonNull)") {
    assert(GraphQLDocuments.usableAs(tpe("[ID!]!"), tpe("[ID]!")))
  }

  test("usableAs: [ID!] declared, [ID!] required is true") {
    assert(GraphQLDocuments.usableAs(tpe("[ID!]"), tpe("[ID!]")))
  }

  test("usableAs: [[ID!]!]! declared, [[ID]] required is true (recurses through nested lists)") {
    assert(GraphQLDocuments.usableAs(tpe("[[ID!]!]!"), tpe("[[ID]]")))
  }

  test("usableAs: ID! declared, [ID] required is false (scalar can't satisfy a list)") {
    assert(!GraphQLDocuments.usableAs(tpe("ID!"), tpe("[ID]")))
  }

  test("usableAs: [ID] declared, ID required is false (list can't satisfy a scalar)") {
    assert(!GraphQLDocuments.usableAs(tpe("[ID]"), tpe("ID")))
  }

  // spliceScopes

  test("spliceScopes: a splice in a single operation sees that operation's variables") {
    val doc    = parseDoc("query ($ep: Episode!) { hero { clue_splice_0: __typename } }")
    val scopes = GraphQLDocuments.spliceScopes(doc)
    assertEquals(scopes(0).keySet, Set("ep"))
    assertEquals(scopes(0)("ep").name, "Episode!")
  }

  test("spliceScopes: two operations each see only their own splice's own variables") {
    val doc    = parseDoc(
      "query A($ep: Episode!) { clue_splice_0: __typename } " +
        "query B($id: ID!) { clue_splice_1: __typename }"
    )
    val scopes = GraphQLDocuments.spliceScopes(doc)
    assertEquals(scopes(0).keySet, Set("ep"))
    assertEquals(scopes(1).keySet, Set("id"))
  }

  test(
    "spliceScopes: a splice inside a fragment is in scope when every operation declares the variable identically"
  ) {
    val doc    = parseDoc(
      "query A($ep: Episode!) { hero { ...f } } query B($ep: Episode!) { hero { ...f } } " +
        "fragment f on Character { clue_splice_0: __typename }"
    )
    val scopes = GraphQLDocuments.spliceScopes(doc)
    assertEquals(scopes(0).keySet, Set("ep"))
  }

  test(
    "spliceScopes: a splice inside a fragment is out of scope when only one operation declares the variable"
  ) {
    val doc    = parseDoc(
      "query A($ep: Episode!) { hero { ...f } } query B { hero { ...f } } " +
        "fragment f on Character { clue_splice_0: __typename }"
    )
    val scopes = GraphQLDocuments.spliceScopes(doc)
    assertEquals(scopes(0).keySet, Set.empty[String])
  }

  // unusedVariables

  test("unusedVariables: a declared, unreferenced variable is reported; a referenced one is not") {
    val doc = parseDoc("query ($used: ID!, $unused: ID!) { character(id: $used) { name } }")
    assertEquals(GraphQLDocuments.unusedVariables(doc, Map.empty), List("operation" -> "unused"))
  }

  test("unusedVariables: a variable referenced only inside a directive argument counts as used") {
    val doc = parseDoc("query ($x: Boolean!) { hero { name @include(if: $x) } }")
    assertEquals(GraphQLDocuments.unusedVariables(doc, Map.empty), Nil)
  }

  test(
    "unusedVariables: a variable referenced only inside a nested list/object argument value counts as used"
  ) {
    val doc = parseDoc("query ($id: ID!) { humans(filter: {ids: [$id]}) { name } }")
    assertEquals(GraphQLDocuments.unusedVariables(doc, Map.empty), Nil)
  }

  test(
    "unusedVariables: a variable referenced only inside a spread fragment counts as used, including one level of fragment-to-fragment spreading"
  ) {
    val doc = parseDoc(
      "query ($ep: Episode!) { hero { ...f1 } } " +
        "fragment f1 on Character { ...f2 } " +
        "fragment f2 on Character { friends(episode: $ep) { name } }"
    )
    assertEquals(GraphQLDocuments.unusedVariables(doc, Map.empty), Nil)
  }

  test(
    "unusedVariables: with two operations, a variable declared by A and used only by B is reported against A"
  ) {
    val doc = parseDoc(
      "query A($id: ID!) { hero } query B($id: ID!) { character(id: $id) { name } }"
    )
    assertEquals(GraphQLDocuments.unusedVariables(doc, Map.empty), List("operation [A]" -> "id"))
  }

  test("unusedVariables: a variable required by a spliced subquery counts as used") {
    val doc = parseDoc("query ($ep: Episode!) { hero { clue_splice_0: __typename } }")
    assertEquals(GraphQLDocuments.unusedVariables(doc, Map(0 -> Set("ep"))), Nil)
    assertEquals(GraphQLDocuments.unusedVariables(doc, Map.empty), List("operation" -> "ep"))
  }

  test(
    "unusedVariables: a variable referenced only in an operation-level directive counts as used"
  ) {
    val doc = parseDoc("query ($cond: Boolean!) @live(if: $cond) { hero { name } }")
    assertEquals(GraphQLDocuments.unusedVariables(doc, Map.empty), Nil)
  }

  test(
    "unusedVariables: a variable referenced only in a fragment-definition directive counts as used"
  ) {
    val doc = parseDoc(
      "query ($cond: Boolean!) { hero { ...f } } fragment f on Character @include(if: $cond) { name }"
    )
    assertEquals(GraphQLDocuments.unusedVariables(doc, Map.empty), Nil)
  }

  test("unusedVariables: a duplicate variable declaration yields one warning, not two") {
    val doc = parseDoc("query ($a: ID!, $a: ID!) { hero }")
    assertEquals(GraphQLDocuments.unusedVariables(doc, Map.empty), List("operation" -> "a"))
  }

  // referencedVariables

  test("referencedVariables: picks up a reference from the selection set") {
    val doc = parseDoc("{ character(id: $id) { name } }")
    assertEquals(GraphQLDocuments.referencedVariables(doc, Map.empty), Set("id"))
  }

  test("referencedVariables: picks up a reference from a fragment definition") {
    val doc = parseDoc(
      "{ hero { ...f } } fragment f on Character { friends(episode: $ep) { name } }"
    )
    assertEquals(GraphQLDocuments.referencedVariables(doc, Map.empty), Set("ep"))
  }

  test("referencedVariables: picks up a reference from a directive") {
    val doc = parseDoc("{ hero { name @include(if: $x) } }")
    assertEquals(GraphQLDocuments.referencedVariables(doc, Map.empty), Set("x"))
  }

  test("referencedVariables: picks up a reference from spliceRequirements") {
    val doc = parseDoc("{ hero { clue_splice_0: __typename } }")
    assertEquals(GraphQLDocuments.referencedVariables(doc, Map(0 -> Set("ep"))), Set("ep"))
    assertEquals(GraphQLDocuments.referencedVariables(doc, Map.empty), Set.empty[String])
  }

  // unusedFragments

  test("unusedFragments: reports a fragment never spread") {
    val doc = parseDoc("query { hero { name } } fragment f on Character { name }")
    assertEquals(GraphQLDocuments.unusedFragments(doc), List("f"))
  }

  test("unusedFragments: does not report a fragment spread from an operation") {
    val doc = parseDoc("query { hero { ...f } } fragment f on Character { name }")
    assertEquals(GraphQLDocuments.unusedFragments(doc), Nil)
  }

  test("unusedFragments: does not report a fragment spread from another fragment") {
    val doc = parseDoc(
      "query { hero { ...f1 } } fragment f1 on Character { ...f2 } fragment f2 on Character { name }"
    )
    assertEquals(GraphQLDocuments.unusedFragments(doc), Nil)
  }

  test("unusedFragments: an inline fragment is not a spread") {
    val doc = parseDoc("query { hero { ... on Droid { name } } } fragment f on Character { name }")
    assertEquals(GraphQLDocuments.unusedFragments(doc), List("f"))
  }
}
