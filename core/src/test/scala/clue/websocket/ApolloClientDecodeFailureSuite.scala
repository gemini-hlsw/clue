// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.websocket

import cats.data.Ior
import cats.effect.*
import cats.effect.std.SecureRandom
import cats.syntax.all.*
import clue.model.GraphQLQuery
import clue.model.GraphQLResponse
import clue.model.StreamingMessage
import io.circe.DecodingFailure
import io.circe.Json
import munit.CatsEffectSuite
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.testing.TestingLogger

import scala.concurrent.duration.*

/**
 * Tests for a `next` payload that does not match the `Data` decoder of its subscription. The
 * failure must stay inside that one subscription.
 */
final class ApolloClientDecodeFailureSuite extends CatsEffectSuite:

  private given Logger[IO] = TestingLogger.impl[IO]()

  private val Timeout: FiniteDuration = 5.seconds

  private def connectedClient(
    backend: TestWebSocketBackend[IO]
  ): IO[ApolloClient[IO, String, Unit]] =
    for
      given SecureRandom[IO]            <- SecureRandom.javaSecuritySecureRandom[IO]
      given WebSocketBackend[IO, String] = backend
      client                            <- ApolloClient.of[IO, String, Unit]("ws://test")
      _                                 <- client.connect()
    yield client

  /** The subscription id that the client generated for the given query text. */
  private def idOf(backend: TestWebSocketBackend[IO], query: String): IO[String] =
    backend.sent.map(
      _.collectFirst {
        case StreamingMessage.FromClient.Subscribe(id, request) if request.query.value === query =>
          id
      }.get
    )

  private def next(id: String, data: Json): StreamingMessage.FromServer =
    StreamingMessage.FromServer.Next(id, GraphQLResponse(Ior.right(data)))

  test("A bad payload does not raise out of the message handler"):
    val query = "subscription { a }"
    for
      backend <- TestWebSocketBackend[IO]()
      client  <- connectedClient(backend)
      outcome <- client
                   .subscribeInternal[Int](GraphQLQuery(query))
                   .use: _ =>
                     idOf(backend, query).flatMap: id =>
                       backend.emit(next(id, Json.fromString("not an int"))).attempt
    yield assertEquals(outcome, ().asRight)

  test("A bad payload raises on its own subscription stream"):
    val query = "subscription { a }"
    for
      backend <- TestWebSocketBackend[IO]()
      client  <- connectedClient(backend)
      outcome <- client
                   .subscribeInternal[Int](GraphQLQuery(query))
                   .use: stream =>
                     for
                       id    <- idOf(backend, query)
                       fiber <- stream.head.compile.lastOrError.attempt.start
                       _     <- backend.emit(next(id, Json.fromString("not an int")))
                       r     <- fiber.joinWithNever.timeout(Timeout)
                     yield r
    yield assert(outcome.swap.exists(_.isInstanceOf[DecodingFailure]), clue = outcome)

  test("A bad payload for one subscription does not disturb another subscription"):
    val queryA = "subscription { a }"
    val queryB = "subscription { b }"
    for
      backend <- TestWebSocketBackend[IO]()
      client  <- connectedClient(backend)
      outcome <- client
                   .subscribeInternal[Int](GraphQLQuery(queryA))
                   .use: streamA =>
                     client
                       .subscribeInternal[Int](GraphQLQuery(queryB))
                       .use: streamB =>
                         for
                           idA    <- idOf(backend, queryA)
                           idB    <- idOf(backend, queryB)
                           fiberA <- streamA.head.compile.lastOrError.attempt.start
                           fiberB <- streamB.head.compile.lastOrError.attempt.start
                           _      <- backend.emit(next(idA, Json.fromString("not an int")))
                           _      <- backend.emit(next(idB, Json.fromInt(2)))
                           a      <- fiberA.joinWithNever.timeout(Timeout)
                           b      <- fiberB.joinWithNever.timeout(Timeout)
                         yield (a, b)
    yield
      assert(outcome._1.isLeft, clue = outcome._1)
      assertEquals(outcome._2.map(_.data), 2.some.asRight)
