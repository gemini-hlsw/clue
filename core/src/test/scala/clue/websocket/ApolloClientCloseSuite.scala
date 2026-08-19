// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.websocket

import cats.effect.*
import cats.effect.std.SecureRandom
import cats.syntax.all.*
import clue.DisconnectedException
import clue.model.GraphQLQuery
import clue.model.GraphQLResponse
import clue.model.StreamingMessage
import io.circe.Json
import munit.CatsEffectSuite
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.testing.TestingLogger

import scala.concurrent.duration.*

/**
 * Tests for the close path without a reconnection. Every subscription must terminate. It must not
 * wait forever.
 */
final class ApolloClientCloseSuite extends CatsEffectSuite:

  private given Logger[IO] = TestingLogger.impl[IO]()

  private val Timeout: FiniteDuration = 5.seconds

  private def clientOn(
    backend:  TestWebSocketBackend[IO],
    strategy: ReconnectionStrategy = ReconnectionStrategy.never
  ): IO[ApolloClient[IO, String, Unit]] =
    for
      given SecureRandom[IO]            <- SecureRandom.javaSecuritySecureRandom[IO]
      given WebSocketBackend[IO, String] = backend
      client                            <- ApolloClient.of[IO, String, Unit]("ws://test", "test", strategy)
    yield client

  private def subscription(
    client: ApolloClient[IO, String, Unit]
  ): Resource[IO, fs2.Stream[IO, GraphQLResponse[Json]]] =
    client.subscribeInternal[Json](GraphQLQuery("subscription { x }"))

  test("A close without a reconnection strategy terminates the subscription"):
    for
      backend <- TestWebSocketBackend[IO]()
      client  <- clientOn(backend)
      _       <- client.connect()
      outcome <- subscription(client).use: stream =>
                   for
                     fiber  <- stream.compile.toList.attempt.start
                     _      <- backend.closeFromServer(CloseParams(1006, "gone").asRight)
                     result <- fiber.joinWithNever.timeout(Timeout)
                   yield result
    yield assertEquals(outcome, DisconnectedException("1006: gone").asLeft)

  test("A close without a reconnection strategy terminates every subscription"):
    for
      backend  <- TestWebSocketBackend[IO]()
      client   <- clientOn(backend)
      _        <- client.connect()
      outcomes <- subscription(client).use: first =>
                    subscription(client).use: second =>
                      for
                        fiberA <- first.compile.toList.attempt.start
                        fiberB <- second.compile.toList.attempt.start
                        _      <- backend.closeFromServer(CloseParams(1006, "gone").asRight)
                        a      <- fiberA.joinWithNever.timeout(Timeout)
                        b      <- fiberB.joinWithNever.timeout(Timeout)
                      yield (a, b)
    yield
      assertEquals(outcomes._1, DisconnectedException("1006: gone").asLeft)
      assertEquals(outcomes._2, DisconnectedException("1006: gone").asLeft)

  test("A close while connecting terminates the subscription"):
    // The strategy reconnects once, on the close with reason "retry". The second close
    // then finds the client in the Connecting state and gives up.
    val strategy: ReconnectionStrategy = (_, reason) =>
      reason match
        case Right(Right(CloseParams(_, Some("retry")))) => Duration.Zero.some
        case _                                           => none

    for
      backend <- TestWebSocketBackend[IO]()
      client  <- clientOn(backend, strategy)
      _       <- client.connect()
      outcome <- subscription(client).use: stream =>
                   for
                     fiber <- stream.compile.toList.attempt.start
                     // The reconnection must not complete, so that the client stays Connecting.
                     _     <- backend.autoAck(false)
                     _     <- backend.closeFromServer(CloseParams(1000, "retry").asRight).start
                     // The second connection_init proves that the client reconnects.
                     _     <- backend
                                .awaitSent(
                                  _.count(
                                    _.isInstanceOf[
                                      StreamingMessage.FromClient.ConnectionInit
                                    ]
                                  ) === 2
                                )
                                .timeout(Timeout)
                     _     <- backend.closeFromServer(CloseParams(4000, "gone").asRight)
                     r     <- fiber.joinWithNever.timeout(Timeout)
                   yield r
    yield assertEquals(outcome, DisconnectedException("4000: gone").asLeft)
