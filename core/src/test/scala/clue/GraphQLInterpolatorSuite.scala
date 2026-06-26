// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import io.circe.Json

// A subquery declaring a required variable, used to exercise the `gql` caller-check.
object InterpolatorTestSub extends GraphQLSubquery.Typed[Unit, Json] {
  type Variables = "($ep: Episode!)"
  override val subquery: String = "{ hero(episode: $ep) { name } }"
}

class GraphQLInterpolatorSuite extends munit.FunSuite {

  test("gql assembles the document like s-interpolation") {
    val doc = gql"query ($$ep: Episode!) $InterpolatorTestSub"
    assertEquals(doc, "query ($ep: Episode!) { hero(episode: $ep) { name } }")
  }

  test("gql passes through a spliced value that declares no variables") {
    val doc = gql"query { hero } trailing ${1}"
    assertEquals(doc, "query { hero } trailing 1")
  }

  test("a required variable the operation does not declare is a compile error") {
    val errors = compileErrors("""gql"query { $InterpolatorTestSub }"""")
    assert(errors.contains("does not declare variable $ep"), errors)
  }

  test("a required variable declared with an incompatible type is a compile error") {
    val errors = compileErrors("""gql"query ($$ep: String!) $InterpolatorTestSub"""")
    assert(errors.contains("usable as Episode!"), errors)
  }

  test("a non-null operation variable satisfies a nullable requirement") {
    // Sanity for the "usable as" rule: Episode! is usable where Episode is required.
    assertEquals(usableProbe, "query ($ep: Episode!) { hero(episode: $ep) { name } }")
  }

  private val usableProbe: String =
    gql"query ($$ep: Episode!) $InterpolatorTestSub"
}
