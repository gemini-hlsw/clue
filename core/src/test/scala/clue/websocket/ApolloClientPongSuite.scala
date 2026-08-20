// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.websocket

import cats.effect.*
import cats.syntax.all.*
import clue.PersistentClientStatus
import clue.model.GraphQLQuery
import io.circe.Json

final class ApolloClientPongSuite extends ApolloClientSuite:

  test("A pong from the server leaves the client connected"):
    for
      backend <- TestWebSocketBackend[IO]()
      client  <- clientOn(backend)
      _       <- backend.emitRaw("""{"type":"pong"}""")
      status  <- client.status
    yield assertEquals(status, PersistentClientStatus.Connected)

  test("A pong with a payload leaves the client connected"):
    for
      backend <- TestWebSocketBackend[IO]()
      client  <- clientOn(backend)
      _       <- backend.emitRaw("""{"type":"pong","payload":{"ok":true}}""")
      status  <- client.status
    yield assertEquals(status, PersistentClientStatus.Connected)

  test("A pong from the server draws no reply"):
    for
      backend <- TestWebSocketBackend[IO]()
      client  <- clientOn(backend)
      before  <- backend.sent
      _       <- backend.emitRaw("""{"type":"pong"}""")
      after   <- backend.sent
    yield assertEquals(after, before)

  test("A pong from the server does not disturb an active subscription"):
    for
      backend <- TestWebSocketBackend[IO]()
      client  <- clientOn(backend)
      result  <- client
                   .subscribeInternal[Json](GraphQLQuery("subscription { x }"))
                   .use: stream =>
                     for
                       id    <- backend.awaitSubscribeId()
                       fiber <- stream.head.compile.lastOrError.start
                       _     <- backend.emitRaw("""{"type":"pong"}""")
                       _     <- backend.emitNext(id, Json.fromInt(1))
                       r     <- fiber.joinWithNever
                     yield r
    yield assertEquals(result.data, Json.fromInt(1).some)
