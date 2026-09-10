// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import GraphQLLexer.*
import Token.*

class GraphQLLexerSuite extends munit.FunSuite {

  private def tokensOf(text: String): Vector[Token] =
    tokenize(text).fold(e => fail(s"expected success, got $e"), identity)

  test("every punctuator is one token") {
    assertEquals(
      tokensOf("! $ & ( ) ... : = @ [ ] { } |").map {
        case p: Punct => p.value; case t => fail(s"$t")
      },
      Vector("!", "$", "&", "(", ")", "...", ":", "=", "@", "[", "]", "{", "}", "|")
    )
  }

  test("a Name") {
    assertEquals(tokensOf("_foo1Bar"), Vector(Name("_foo1Bar", 0)))
  }

  test("an IntVal") {
    assertEquals(tokensOf("-42"), Vector(IntVal("-42", 0)))
  }

  test("a FloatVal with fraction and exponent") {
    assertEquals(tokensOf("-4.2e+1"), Vector(FloatVal("-4.2e+1", 0)))
  }

  test("commas and whitespace are ignored") {
    assertEquals(tokensOf("a,\t,\n ,b"), Vector(Name("a", 0), Name("b", 7)))
  }

  test("a # comment is skipped to end of line") {
    assertEquals(tokensOf("a # comment\nb"), Vector(Name("a", 0), Name("b", 12)))
  }

  test("string escapes, including \\\" and A") {
    // Built from pieces (rather than one escaped literal) so the GraphQL source text - each escape
    // is a literal backslash followed by its letter, for the lexer to decode - is unambiguous.
    val bs  = "\\"
    val src =
      "\"" + "a" + bs + "\"" + "A" + bs + bs + bs + "/" + bs + "b" + bs + "f" + bs + "n" + bs + "r" + bs + "t" + "A" + "\""
    assertEquals(tokensOf(src), Vector(Str("a\"A\\/\b\f\n\r\tA", 0)))
  }

  test("a block string containing a quote and a newline") {
    assertEquals(tokensOf("\"\"\"a \"b\nc\"\"\""), Vector(Str("a \"b\nc", 0)))
  }

  test("a block string containing an escaped triple-quote") {
    assertEquals(tokensOf("\"\"\"a \\\"\"\" b\"\"\""), Vector(Str("a \"\"\" b", 0)))
  }

  test("... is one token") {
    assertEquals(tokensOf("..."), Vector(Punct("...", 0)))
  }

  test("a lone . is an error") {
    assertEquals(tokenize(".."), Left(LexError("unexpected character '.'", 0)))
  }

  test("a number immediately followed by a Name is an error") {
    assertEquals(tokenize("1Name"), Left(LexError("invalid number", 0)))
  }

  test("an unknown character is an error with its position") {
    assertEquals(tokenize("a % b"), Left(LexError("unexpected character '%'", 2)))
  }

  test("an unterminated string is an error") {
    assertEquals(tokenize("\"abc"), Left(LexError("unterminated string", 0)))
  }

  test("an unterminated block string is an error") {
    assertEquals(tokenize("\"\"\"abc"), Left(LexError("unterminated string", 0)))
  }

  test("tokenizeParts splices a hole between two parts, unescaping $$") {
    val result = tokenizeParts(List("query ($$a: ID!) { x ", " }"))
    assertEquals(
      result,
      Right(
        Vector(
          Name("query", 0),
          Punct("(", 6),
          Punct("$", 7),
          Name("a", 8),
          Punct(":", 9),
          Name("ID", 11),
          Punct("!", 13),
          Punct(")", 14),
          Punct("{", 16),
          Name("x", 18),
          Splice(0, 20),
          Punct("}", 21)
        )
      )
    )
  }

  test("a string spanning a splice is an error") {
    val result = tokenizeParts(List("query { x: \"abc", "def\" }"))
    assert(result.isLeft, result)
    assert(result.swap.exists(_.message == "unterminated string"), result)
  }
}
