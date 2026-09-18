// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
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

// Declares no variables at all: exercises splicing a subquery that has nothing to check.
object InterpolatorTestSubPlain extends GraphQLSubquery.Typed[Unit, Json] {
  override val subquery = gql"{ name }"
}

// Declares a variable of LIST type, to exercise structural (not textual) type comparison.
object InterpolatorTestSubList extends GraphQLSubquery.Typed[Unit, Json] {
  type VariableDefs = "($ids: [ID!])"
  override val subquery = gql"{ humans(ids: $$ids) { name } }"
}

// A pre-generation `@GraphQLStub` placeholder (no `GraphQLSubquery` supertype), as the generator
// leaves it before the scalafix rule replaces it with a real subquery. Exercises that the macro
// tolerates splicing it.
@clue.annotation.GraphQLStub
object InterpolatorTestStub

class GraphQLInterpolatorSuite extends munit.FunSuite {

  test("gql assembles the document like s-interpolation") {
    val doc = gql"query ($$ep: Episode!) $InterpolatorTestSub"
    assertEquals(doc.value, "query ($ep: Episode!) { hero(episode: $ep) { name } }")
  }

  test("gql passes through a spliced value that declares no variables") {
    val doc = gql"query { hero $InterpolatorTestSubPlain }"
    assertEquals(doc.value, "query { hero { name } }")
  }

  test("a required variable the operation does not declare is a compile error") {
    val errors = compileErrors("""gql"query $InterpolatorTestSub"""")
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
    val errors = compileErrors("""gql"query $InterpolatorTestParent"""")
    assert(errors.contains("does not declare variable $ep"), errors)

    val doc = gql"query ($$ep: Episode!) $InterpolatorTestParent"
    assertEquals(doc.value, "query ($ep: Episode!) { friends { hero(episode: $ep) { name } } }")
  }

  test("a fragment before the operation still sees the declared variables") {
    val doc =
      gql"fragment f on Character { name } query ($$ep: Episode!) { hero $InterpolatorTestSub }"
    assertEquals(
      doc.value,
      "fragment f on Character { name } query ($ep: Episode!) { hero { hero(episode: $ep) { name } } }"
    )
  }

  test("a fragment's field arguments are not mistaken for the header") {
    val errors = compileErrors(
      """gql"fragment f on Character { hero(episode: NEWHOPE) { name } } query $InterpolatorTestSub""""
    )
    assert(errors.contains("does not declare variable $ep"), errors)
  }

  test("a named operation's header is found") {
    val doc = gql"query Foo($$ep: Episode!) $InterpolatorTestSub"
    assertEquals(doc.value, "query Foo($ep: Episode!) { hero(episode: $ep) { name } }")
  }

  test("a syntax error is a compile error") {
    val errors = compileErrors("""gql"query { hero "  """)
    assert(errors.contains("gql:"), errors)
  }

  test("a list variable type is compared structurally") {
    val doc = gql"query ($$ids: [ID!]!) $InterpolatorTestSubList"
    assertEquals(doc.value, "query ($ids: [ID!]!) { humans(ids: $ids) { name } }")
  }

  test("a non-subquery splice is a compile error") {
    val errors = compileErrors("""gql"query { hero ${1} }"""")
    assert(errors.contains("only a GraphQLSubquery"), errors)
  }

  test("a pre-generation @GraphQLStub placeholder may be spliced") {
    // `InterpolatorTestStub` has no `subquery`/`toString` override (it's a bare placeholder), so
    // rather than hardcode its default `Object#toString`, compare against plain s-interpolation of
    // the same literal, per `gql`'s contract of producing exactly what `s"..."` would.
    val doc = gql"query { hero $InterpolatorTestStub }"
    assertEquals(doc.value, s"query { hero $InterpolatorTestStub }")
  }

  test("a splice in a multi-operation document sees only its own operation's declared variables") {
    val doc = gql"query A($$ep: Episode!) $InterpolatorTestSub query B { hero }"
    assertEquals(
      doc.value,
      "query A($ep: Episode!) { hero(episode: $ep) { name } } query B { hero }"
    )
  }

  test(
    "a named operation's own splice cannot be satisfied by another operation's declaration (regression)"
  ) {
    val errors =
      compileErrors("""gql"query A($$ep: Episode!) { hero } query B $InterpolatorTestSub"""")
    assert(errors.contains("does not declare variable $ep"), errors)
    assert(errors.contains("operation [B]"), errors)
  }

  test(
    "a splice inside a fragment definition compiles when the sole operation declares the variable"
  ) {
    val doc =
      gql"query ($$ep: Episode!) { hero { ...f } } fragment f on Character { friends $InterpolatorTestSub }"
    assertEquals(
      doc.value,
      "query ($ep: Episode!) { hero { ...f } } fragment f on Character { friends { hero(episode: $ep) { name } } }"
    )
  }

  test("a deeply nested input value (six levels) compiles") {
    val doc = gql"query { f(l: {a:{b:{c:{d:{e:{f:1}}}}}}) }"
    assertEquals(doc.value, "query { f(l: {a:{b:{c:{d:{e:{f:1}}}}}}) }")
  }
}
