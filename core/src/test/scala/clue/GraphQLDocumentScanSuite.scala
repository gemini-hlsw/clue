// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import GraphQLDocumentScan.*
import VarType.*

class GraphQLDocumentScanSuite extends munit.FunSuite {

  private def tokensOf(text: String): Vector[GraphQLLexer.Token] =
    GraphQLLexer.tokenize(text).fold(e => fail(s"expected a successful tokenize, got $e"), identity)

  private def varDefsOf(text: String): Map[String, VarType] =
    parseVarDefs(tokensOf(text)).fold(e => fail(s"expected a successful parse, got $e"), identity)

  test(
    "parseVarDefs: named, list, non-null types, a nested default value containing ')', a directive"
  ) {
    val defs = varDefsOf("""($a: T = [1, {x: "a)b"}] @dir, $b: [U!]!)""")
    assertEquals(
      defs,
      Map("a" -> Named("T", nonNull = false),
          "b" -> ListOf(Named("U", nonNull = true), nonNull = true)
      )
    )
  }

  test("usableAs truth table") {
    val episodeNN  = Named("Episode", nonNull = true)
    val episode    = Named("Episode", nonNull = false)
    val idListNN   = ListOf(Named("ID", nonNull = true), nonNull = true)
    val idList     = ListOf(Named("ID", nonNull = true), nonNull = false)
    val idNullList = ListOf(Named("ID", nonNull = false), nonNull = false)

    assert(usableAs(episodeNN, episodeNN))
    assert(usableAs(episodeNN, episode))
    assert(!usableAs(episode, episodeNN))
    assert(!usableAs(Named("Episode", nonNull = true), Named("Other", nonNull = true)))
    assert(usableAs(idListNN, idList))
    assert(!usableAs(idNullList, idList))
  }

  test("operationVars: shorthand document declares none, at index 0") {
    assertEquals(operationVars(tokensOf("{ hero }")), Right((Map.empty[String, VarType], 0)))
  }

  test("operationVars: an anonymous operation with var-defs") {
    val tokens             = tokensOf("query ($ep: Episode!) { hero }")
    val Right((vars, idx)) = operationVars(tokens): @unchecked
    assertEquals(vars, Map("ep" -> Named("Episode", nonNull = true)))
    assertEquals(tokens(idx), GraphQLLexer.Token.Punct("{", tokens(idx).pos))
  }

  test("operationVars: a named operation with var-defs") {
    val tokens             = tokensOf("query Foo($ep: Episode!) { hero }")
    val Right((vars, idx)) = operationVars(tokens): @unchecked
    assertEquals(vars, Map("ep" -> Named("Episode", nonNull = true)))
    assertEquals(tokens(idx), GraphQLLexer.Token.Punct("{", tokens(idx).pos))
  }

  test("operationVars: an operation without parens declares none") {
    assertEquals(operationVars(tokensOf("mutation { hero }")).map(_._1),
                 Right(Map.empty[String, VarType])
    )
  }

  test(
    "operationVars: a fragment with field arguments before the operation still finds the header"
  ) {
    val doc              =
      "fragment f on Character { hero(episode: NEWHOPE) { name } } query ($ep: Episode!) { hero }"
    val Right((vars, _)) = operationVars(tokensOf(doc)): @unchecked
    assertEquals(vars, Map("ep" -> Named("Episode", nonNull = true)))
  }

  test("parseVarDefs skips a directive with arguments on a variable definition") {
    val tokens =
      GraphQLLexer.tokenize("""($a: ID @dir(x: 1, y: "s)") @other, $b: Int)""").toOption.get
    assertEquals(
      parseVarDefs(tokens),
      Right(
        Map("a" -> VarType.Named("ID", nonNull = false),
            "b" -> VarType.Named("Int", nonNull = false)
        )
      )
    )
  }
}
