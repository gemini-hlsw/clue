// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import GraphQLText.*

class GraphQLTextSuite extends munit.FunSuite {

  // Parts as `StringContext` hands them to the macro: `$$`-escaped, one per literal segment.
  test("parse splits the operation header from the body") {
    val Parsed(vars, body) = parse(List("query ($$ep: Episode!, $$id: ID) { hero(episode: $$ep) "))
    assertEquals(vars, Map("ep" -> "Episode!", "id" -> "ID"))
    assertEquals(body, " { hero(episode: $ep) ")
  }

  test("parse joins the parts after a splice into the body") {
    val Parsed(_, body) = parse(List("query ($$ep: Episode!) { friends ", " }"))
    assertEquals(body, " { friends  }")
  }

  test("a subquery body has no header: a field's arguments are not var-defs") {
    val Parsed(vars, body) = parse(List("{ hero(episode: $$ep) { name } }"))
    assertEquals(vars, Map.empty[String, String])
    assertEquals(body, "{ hero(episode: $ep) { name } }")
  }

  test("parseVarDefs handles defaults and list types") {
    assertEquals(
      parseVarDefs("($a: [ID!]! = [\"x\", \"y\"], $b: Int = 1)"),
      Map("a" -> "[ID!]!", "b" -> "Int")
    )
  }

  test("a declared variable never referenced is unused") {
    assertEquals(unusedVariables(Set("unused"), " { hero { name } }", Set.empty), List("unused"))
  }

  test("a variable referenced in the body is used") {
    assertEquals(unusedVariables(Set("id"), " { character(id: $id) }", Set.empty), Nil)
  }

  test("a variable required by a spliced subquery is used") {
    assertEquals(unusedVariables(Set("ep"), " { friends  }", Set("ep")), Nil)
  }

  test("only the over-declared variable is reported, sorted") {
    assertEquals(
      unusedVariables(Set("zz", "ep", "aa"), " { friends  }", Set("ep")),
      List("aa", "zz")
    )
  }

  test("a defined fragment never spread is unused") {
    assertEquals(
      unusedFragments(" { hero { name } } fragment fields on Character { id }"),
      List("fields")
    )
  }

  test("a spread fragment is used, and an inline fragment is not a spread") {
    assertEquals(
      unusedFragments(
        " { hero { ...fields ... on Droid { primaryFunction } } } fragment fields on Character { id }"
      ),
      Nil
    )
  }

  test("usableAs: same base type, non-null requirement needs non-null declaration") {
    assert(usableAs("Episode!", "Episode!"))
    assert(usableAs("Episode!", "Episode"))
    assert(!usableAs("Episode", "Episode!"))
    assert(!usableAs("String!", "Episode!"))
  }
}
