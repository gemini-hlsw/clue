// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model.json

import cats.syntax.all.*
import clue.model.StreamingMessage.FromClient
import io.circe.Json
import io.circe.JsonObject
import io.circe.parser.decode
import io.circe.syntax.*

final class FromClientPingCodecSuite extends munit.FunSuite:

  test("A ping without a payload decodes as FromClient.Ping"):
    assertEquals(decode[FromClient]("""{"type":"ping"}"""), Right(FromClient.Ping()))

  test("A ping with a payload decodes as FromClient.Ping"):
    val payload = JsonObject("seq" -> Json.fromInt(7))
    assertEquals(
      decode[FromClient]("""{"type":"ping","payload":{"seq":7}}"""),
      Right(FromClient.Ping(payload.some))
    )

  test("A ping with a null payload decodes as FromClient.Ping"):
    assertEquals(decode[FromClient]("""{"type":"ping","payload":null}"""), Right(FromClient.Ping()))

  test("A ping without a payload encodes without a payload field"):
    assertEquals((FromClient.Ping(): FromClient).asJson,
                 Json.obj("type" -> Json.fromString("ping"))
    )

  test("A ping with a payload encodes with the payload field"):
    val payload = JsonObject("seq" -> Json.fromInt(7))
    assertEquals(
      (FromClient.Ping(payload.some): FromClient).asJson,
      Json.obj("type" -> Json.fromString("ping"), "payload" -> payload.asJson)
    )

  test("A pong does not decode as FromClient.Ping"):
    assertEquals(decode[FromClient]("""{"type":"pong"}"""), Right(FromClient.Pong()))
