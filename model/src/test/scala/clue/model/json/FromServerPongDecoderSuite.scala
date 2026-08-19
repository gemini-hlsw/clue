// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model.json

import cats.syntax.all.*
import clue.model.StreamingMessage.FromServer
import io.circe.Json
import io.circe.JsonObject
import io.circe.parser.decode
import io.circe.syntax.*

final class FromServerPongDecoderSuite extends munit.FunSuite:

  test("A pong without a payload decodes as FromServer.Pong"):
    assertEquals(decode[FromServer]("""{"type":"pong"}"""), Right(FromServer.Pong()))

  test("A pong with a payload decodes as FromServer.Pong"):
    val payload = JsonObject("latency" -> Json.fromInt(42))
    assertEquals(
      decode[FromServer]("""{"type":"pong","payload":{"latency":42}}"""),
      Right(FromServer.Pong(payload.some))
    )

  test("A pong with a null payload decodes as FromServer.Pong"):
    assertEquals(decode[FromServer]("""{"type":"pong","payload":null}"""), Right(FromServer.Pong()))

  test("A pong without a payload encodes without a payload field"):
    assertEquals((FromServer.Pong(): FromServer).asJson,
                 Json.obj("type" -> Json.fromString("pong"))
    )
