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

  test("stripMargin strips the assembled document") {
    val doc = gql"""query {
                   |  hero
                   |}""".stripMargin
    assertEquals(doc.value, "query {\n  hero\n}")
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

  test("a document with a fragment before the operation still sees the declared variables") {
    val doc =
      gql"fragment f on Character { name } query ($$ep: Episode!) { hero $InterpolatorTestSub }"
    assertEquals(
      doc.value,
      "fragment f on Character { name } query ($ep: Episode!) { hero { hero(episode: $ep) { name } } }"
    )
  }

  test("a named operation's header is found") {
    val doc = gql"query Foo($$ep: Episode!) $InterpolatorTestSub"
    assertEquals(doc.value, "query Foo($ep: Episode!) { hero(episode: $ep) { name } }")
  }

  test("a lexical error is a compile error") {
    // `StringContext.parts` are raw/undecoded for a custom interpolator (only `$$` needs our own
    // unescaping, per `tokenizeParts`'s doc): a `\"` written inside a `gql"..."` literal reaches the
    // lexer as a literal backslash, not a decoded quote. So to get a document that is genuinely
    // unterminated (rather than one with a stray, invalid `\`), the embedded quote here is a bare
    // `"` inside a triple-quoted `gql"""..."""`, which Scala itself leaves untouched.
    val errors = compileErrors("gql\"\"\"query { hero(name: \"unterminated) }\"\"\"")
    assert(errors.contains("unterminated string"), errors)
  }

  test("a fragment's field arguments are not mistaken for the header") {
    val errors = compileErrors(
      """gql"fragment f on Character { hero(episode: NEWHOPE) { name } } query { $InterpolatorTestSub }""""
    )
    assert(errors.contains("does not declare variable $ep"), errors)
  }
}
