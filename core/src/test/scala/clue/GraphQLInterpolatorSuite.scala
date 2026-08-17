// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import io.circe.Json

// A subquery declaring a required variable, used to exercise the `gql` caller-check.
object InterpolatorTestSub extends GraphQLSubquery.Typed[Unit, Json] {
  type VariableDefs = "($ep: Episode!)"
  override val subquery = gql"{ hero(episode: $$ep) { name } }"
}

// Declares a NULLABLE variable, to exercise the "usable as" relaxation.
object InterpolatorTestSubNullable extends GraphQLSubquery.Typed[Unit, Json] {
  type VariableDefs = "($ep: Episode)"
  override val subquery = gql"{ heroOpt(episode: $$ep) { name } }"
}

// Splices a subquery that requires `$ep`, and declares it: the subquery-into-subquery caller-check
// reads this `VariableDefs` as the declaration site.
object InterpolatorTestParent extends GraphQLSubquery.Typed[Unit, Json] {
  type VariableDefs = "($ep: Episode!)"
  override val subquery = gql"{ friends $InterpolatorTestSub }"
}

class GraphQLInterpolatorSuite extends munit.FunSuite {

  test("gql assembles the document like s-interpolation") {
    val doc = gql"query ($$ep: Episode!) $InterpolatorTestSub"
    assertEquals(doc.value, "query ($ep: Episode!) { hero(episode: $ep) { name } }")
  }

  test("gql passes through a spliced value that declares no variables") {
    val doc = gql"query { hero } trailing ${1}"
    assertEquals(doc.value, "query { hero } trailing 1")
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
    // "usable as": a non-null Episode! is usable where the subquery only needs a nullable Episode.
    val doc = gql"query ($$ep: Episode!) $InterpolatorTestSubNullable"
    assertEquals(doc.value, "query ($ep: Episode!) { heroOpt(episode: $ep) { name } }")
  }

  test("a subquery splicing a subquery assembles like s-interpolation") {
    assertEquals(InterpolatorTestParent.subquery.value,
                 "{ friends { hero(episode: $ep) { name } } }"
    )
  }

  test("a subquery that does not declare a spliced subquery's variable is a compile error") {
    val errors = compileErrors("""
      object UndeclaringParent extends GraphQLSubquery.Typed[Unit, io.circe.Json] {
        override val subquery = gql"{ friends $InterpolatorTestSub }"
      }
      ()
    """)
    assert(errors.contains("does not declare variable $ep"), errors)
    assert(errors.contains("subquery ["), errors)
  }

  test("a subquery declaring a spliced subquery's variable at the wrong type is a compile error") {
    val errors = compileErrors("""
      object WrongTypeParent extends GraphQLSubquery.Typed[Unit, io.circe.Json] {
        type VariableDefs = "($ep: String!)"
        override val subquery = gql"{ friends $InterpolatorTestSub }"
      }
      ()
    """)
    assert(errors.contains("usable as Episode!"), errors)
  }

  test("requirements propagate transitively through a nested subquery") {
    // `InterpolatorTestParent` had to declare `$ep` to splice its child, so an operation splicing
    // the parent must declare it too.
    val errors = compileErrors("""gql"query { $InterpolatorTestParent }"""")
    assert(errors.contains("does not declare variable $ep"), errors)

    val doc = gql"query ($$ep: Episode!) $InterpolatorTestParent"
    assertEquals(doc.value, "query ($ep: Episode!) { friends { hero(episode: $ep) { name } } }")
  }
}
