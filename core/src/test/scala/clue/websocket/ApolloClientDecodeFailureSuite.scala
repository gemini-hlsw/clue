// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.websocket

import cats.effect.*
import cats.syntax.all.*
import clue.model.GraphQLQuery
import io.circe.DecodingFailure
import io.circe.Json

/**
 * Tests for a `next` payload that does not match the `Data` decoder of its subscription. The
 * failure must stay inside that one subscription.
 */
final class ApolloClientDecodeFailureSuite extends ApolloClientSuite:

  test("A bad payload does not raise out of the message handler"):
    val query = "subscription { a }"
    for
      backend <- TestWebSocketBackend[IO]()
      client  <- clientOn(backend)
      outcome <- client
                   .subscribeInternal[Int](GraphQLQuery(query))
                   .use: _ =>
                     backend
                       .awaitSubscribeId(query.some)
                       .flatMap: id =>
                         backend.emitNext(id, Json.fromString("not an int")).attempt
    yield assertEquals(outcome, ().asRight)

  test("A bad payload raises on its own subscription stream"):
    val query = "subscription { a }"
    for
      backend <- TestWebSocketBackend[IO]()
      client  <- clientOn(backend)
      outcome <- client
                   .subscribeInternal[Int](GraphQLQuery(query))
                   .use: stream =>
                     for
                       id    <- backend.awaitSubscribeId(query.some)
                       fiber <- stream.head.compile.lastOrError.attempt.start
                       _     <- backend.emitNext(id, Json.fromString("not an int"))
                       r     <- fiber.joinWithNever.timeout(Timeout)
                     yield r
    yield assert(outcome.swap.exists(_.isInstanceOf[DecodingFailure]), clue = outcome)

  test("A bad payload for one subscription does not disturb another subscription"):
    val queryA = "subscription { a }"
    val queryB = "subscription { b }"
    for
      backend <- TestWebSocketBackend[IO]()
      client  <- clientOn(backend)
      outcome <- client
                   .subscribeInternal[Int](GraphQLQuery(queryA))
                   .use: streamA =>
                     client
                       .subscribeInternal[Int](GraphQLQuery(queryB))
                       .use: streamB =>
                         for
                           idA    <- backend.awaitSubscribeId(queryA.some)
                           idB    <- backend.awaitSubscribeId(queryB.some)
                           fiberA <- streamA.head.compile.lastOrError.attempt.start
                           fiberB <- streamB.head.compile.lastOrError.attempt.start
                           _      <- backend.emitNext(idA, Json.fromString("not an int"))
                           _      <- backend.emitNext(idB, Json.fromInt(2))
                           a      <- fiberA.joinWithNever.timeout(Timeout)
                           b      <- fiberB.joinWithNever.timeout(Timeout)
                         yield (a, b)
    yield
      assert(outcome._1.isLeft, clue = outcome._1)
      assertEquals(outcome._2.map(_.data), 2.some.asRight)
