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
}
