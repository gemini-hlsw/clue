// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model.json

import cats.data.NonEmptyList
import cats.syntax.all.*
import clue.model.GraphQLError
import clue.model.GraphQLError.PathElement
import io.circe.parser.decode

final class GraphQLErrorDecoderSuite extends munit.FunSuite:

  test("PathElement decodes an Int-sized number as IntPathElement"):
    assertEquals(decode[PathElement]("5"), Right(PathElement.int(5)))

  test("PathElement decodes a string as StringPathElement"):
    assertEquals(decode[PathElement]("\"field\""), Right(PathElement.string("field")))

  test("PathElement falls back to a string for numbers that don't fit in an Int"):
    val big = "99999999999999"
    assertEquals(decode[PathElement](big), Right(PathElement.string(big)))

  test("PathElement falls back to a string for non-integral numbers"):
    assertEquals(decode[PathElement]("1.5"), Right(PathElement.string("1.5")))

  test("A GraphQLError with an out-of-range numeric path element still decodes"):
    val json     =
      """{ "message": "boom", "path": ["users", 99999999999999, "name"] }"""
    val expected = GraphQLError(
      "boom",
      path = NonEmptyList
        .of(
          PathElement.string("users"),
          PathElement.string("99999999999999"),
          PathElement.string("name")
        )
        .some
    )
    assertEquals(decode[GraphQLError](json), Right(expected))
