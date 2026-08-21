// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.js

import cats.syntax.all.*
import org.scalajs.dom.URLSearchParams

class FetchJsBackendSuite extends munit.FunSuite {

  // Parse the `?query=...&variables=...&operationName=...` query string back into a key -> value map.
  // `URLSearchParams` is the standard that `buildGetUri` writes, so a value that contains `&`, `=`,
  // `#` or a space is decoded here in the same way that a server decodes it.
  private def parseParams(uri: String): Map[String, String] = {
    val entries = Map.newBuilder[String, String]
    new URLSearchParams(uri.substring(uri.indexOf('?') + 1))
      .forEach((value, name) => entries += name -> value)
    entries.result()
  }

  test("buildGetUri keeps repeated spaces inside a string literal") {
    val query = """query { x(s: "two  spaces") }"""
    val uri   = FetchJsBackend.buildGetUri("https://example.com/gql", query, none, none)
    assertEquals(parseParams(uri)("query"), query)
  }

  test("buildGetUri keeps the indentation of a multi-line document") {
    val query = "query Foo {\n    id\n    name\n}"
    val uri   = FetchJsBackend.buildGetUri("https://example.com/gql", query, none, none)
    assertEquals(parseParams(uri)("query"), query)
  }

  test("buildGetUri trims whitespace around the document") {
    val uri =
      FetchJsBackend.buildGetUri("https://example.com/gql", "  query Foo { id }\n", none, none)
    assertEquals(parseParams(uri)("query"), "query Foo { id }")
  }

  test("buildGetUri does not leak '&' from variable values into extra query params") {
    val variables = """{"name":"a&evil=injected"}"""
    val uri       =
      FetchJsBackend.buildGetUri("https://example.com/gql", "query { id }", variables.some, none)

    val params = parseParams(uri)
    // The malicious '&'/'=' must stay inside the single decoded `variables` value...
    assertEquals(params("variables"), variables)
    // ...and must NOT have created an injected parameter.
    assert(!params.contains("evil"), s"Injected parameter found in: $uri")
    assertEquals(params.keySet, Set("query", "variables"))
  }

  test("buildGetUri does not let '#' truncate the request as a fragment") {
    val variables = """{"note":"a#b"}"""
    val uri       =
      FetchJsBackend.buildGetUri("https://example.com/gql", "query { id }", variables.some, none)

    assert(!uri.contains("#"), s"Unescaped '#' present in: $uri")
    assertEquals(parseParams(uri)("variables"), variables)
  }

  test("buildGetUri encodes the operationName") {
    val uri    =
      FetchJsBackend.buildGetUri("https://example.com/gql", "query { id }", none, "Op&x=1".some)
    val params = parseParams(uri)
    assertEquals(params("operationName"), "Op&x=1")
    assertEquals(params.keySet, Set("query", "operationName"))
  }
}
