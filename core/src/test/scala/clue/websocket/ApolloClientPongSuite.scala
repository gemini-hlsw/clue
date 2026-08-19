// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.websocket

import cats.data.Ior
import cats.effect.*
import cats.effect.std.SecureRandom
import cats.syntax.all.*
import clue.PersistentClientStatus
import clue.model.GraphQLQuery
import clue.model.GraphQLResponse
import clue.model.StreamingMessage
import io.circe.Json
import munit.CatsEffectSuite
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.testing.TestingLogger

final class ApolloClientPongSuite extends CatsEffectSuite:

  private given Logger[IO] = TestingLogger.impl[IO]()

  private def connectedClient(
    backend: TestWebSocketBackend[IO]
  ): IO[ApolloClient[IO, String, Unit]] =
    for
      given SecureRandom[IO]            <- SecureRandom.javaSecuritySecureRandom[IO]
      given WebSocketBackend[IO, String] = backend
      client                            <- ApolloClient.of[IO, String, Unit]("ws://test")
      _                                 <- client.connect()
    yield client

  test("A pong from the server leaves the client connected"):
    for
      backend <- TestWebSocketBackend[IO]()
      client  <- connectedClient(backend)
      _       <- backend.emitRaw("""{"type":"pong"}""")
      status  <- client.status
    yield assertEquals(status, PersistentClientStatus.Connected)

  test("A pong with a payload leaves the client connected"):
    for
      backend <- TestWebSocketBackend[IO]()
      client  <- connectedClient(backend)
      _       <- backend.emitRaw("""{"type":"pong","payload":{"ok":true}}""")
      status  <- client.status
    yield assertEquals(status, PersistentClientStatus.Connected)

  test("A pong from the server draws no reply"):
    for
      backend <- TestWebSocketBackend[IO]()
      client  <- connectedClient(backend)
      before  <- backend.sent
      _       <- backend.emitRaw("""{"type":"pong"}""")
      after   <- backend.sent
    yield assertEquals(after, before)

  test("A pong from the server does not disturb an active subscription"):
    for
      backend <- TestWebSocketBackend[IO]()
      client  <- connectedClient(backend)
      result  <- client
                   .subscribeInternal[Json](GraphQLQuery("subscription { x }"))
                   .use: stream =>
                     for
                       id    <- backend.sent.map(
                                  _.collectFirst { case StreamingMessage.FromClient.Subscribe(i, _) =>
                                    i
                                  }.get
                                )
                       fiber <- stream.head.compile.lastOrError.start
                       _     <- backend.emitRaw("""{"type":"pong"}""")
                       _     <- backend.emit(
                                  StreamingMessage.FromServer
                                    .Next(id, GraphQLResponse(Ior.right(Json.fromInt(1))))
                                )
                       r     <- fiber.joinWithNever
                     yield r
    yield assertEquals(result.data, Json.fromInt(1).some)
