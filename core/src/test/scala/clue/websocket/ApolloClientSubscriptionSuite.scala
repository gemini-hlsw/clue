// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.websocket

import cats.effect.*
import cats.syntax.all.*
import clue.PersistentClientStatus
import clue.model.GraphQLQuery
import clue.model.StreamingMessage
import io.circe.Json

/** Tests for the end of a subscription, whichever side ends it. */
final class ApolloClientSubscriptionSuite extends ApolloClientSuite:

  private val Subscription = "subscription { x }"

  test("A subscription that the server completes logs no error"):
    for
      backend          <- TestWebSocketBackend[IO]()
      (client, logger) <- clientWithLogger(backend)
      _                <- client
                            .subscribeInternal[Json](GraphQLQuery(Subscription))
                            .use: stream =>
                              for
                                fiber <- stream.compile.toList.start
                                id    <- backend.awaitSubscribeId()
                                _     <- backend.emitNext(id, Json.fromInt(1))
                                _     <- backend.emit(StreamingMessage.FromServer.Complete(id))
                                _     <- fiber.joinWithNever.timeout(Timeout)
                              yield ()
      logged           <- errors(logger)
    yield assertEquals(logged, Vector.empty)

  test("A subscription that the server completes draws no complete message back"):
    for
      backend   <- TestWebSocketBackend[IO]()
      client    <- clientOn(backend)
      _         <- client
                     .subscribeInternal[Json](GraphQLQuery(Subscription))
                     .use: stream =>
                       for
                         fiber <- stream.compile.toList.start
                         id    <- backend.awaitSubscribeId()
                         _     <- backend.emit(StreamingMessage.FromServer.Complete(id))
                         _     <- fiber.joinWithNever.timeout(Timeout)
                       yield ()
      completes <- backend.completes
    yield assertEquals(completes, Nil)

  // Nobody reads the stream, so only the complete message can drop the subscription.
  test("A subscription that the server completes is not restarted on a reconnection"):
    for
      backend    <- TestWebSocketBackend[IO]()
      client     <- clientOn(backend, retryOnce)
      _          <- client
                      .subscribeInternal[Json](GraphQLQuery(Subscription))
                      .use: _ =>
                        for
                          id <- backend.awaitSubscribeId()
                          _  <- backend.emit(StreamingMessage.FromServer.Complete(id))
                          _  <- backend.closeFromServer(CloseParams(1006, "retry").asRight)
                          _  <- backend.awaitConnectionInits(2).timeout(Timeout)
                          _  <- IO.sleep(Settle)
                        yield ()
      subscribes <- backend.subscribes
      completes  <- backend.completes
    yield
      assertEquals(subscribes.length, 1)
      assertEquals(completes, Nil)

  test("A subscription that the caller stops after one element sends a complete message"):
    for
      backend   <- TestWebSocketBackend[IO]()
      client    <- clientOn(backend)
      id        <- client
                     .subscribeInternal[Json](GraphQLQuery(Subscription))
                     .use: stream =>
                       for
                         fiber <- stream.take(1).compile.toList.start
                         id    <- backend.awaitSubscribeId()
                         _     <- backend.emitNext(id, Json.fromInt(1))
                         _     <- fiber.joinWithNever.timeout(Timeout)
                       yield id
      completes <- backend.completes
    yield assertEquals(completes, List(id))

  test("A subscription that the caller releases first sends a complete message"):
    for
      backend   <- TestWebSocketBackend[IO]()
      client    <- clientOn(backend)
      id        <- client
                     .subscribeInternal[Json](GraphQLQuery(Subscription))
                     .use(_ => backend.awaitSubscribeId())
      completes <- backend.completes
    yield assertEquals(completes, List(id))

  test("A subscription that starts and ends repeats no client status"):
    for
      backend  <- TestWebSocketBackend[IO]()
      client   <- clientOn(backend)
      statuses <- Ref.of[IO, List[PersistentClientStatus]](Nil)
      watcher  <- client.statusStream.evalTap(s => statuses.update(_ :+ s)).compile.drain.start
      _        <- client
                    .subscribeInternal[Json](GraphQLQuery(Subscription))
                    .use(_ => backend.awaitSubscribeId().timeout(Timeout))
      _        <- IO.sleep(Settle)
      _        <- watcher.cancel
      seen     <- statuses.get
    yield assertEquals(seen, List(PersistentClientStatus.Connected))

  // The stop drops the subscription from the state, so no other event can end its stream.
  test("A subscription whose complete message fails still ends its stream"):
    for
      backend <- TestWebSocketBackend[IO]()
      client  <- clientOn(backend)
      reader  <- client
                   .subscribeInternal[Json](GraphQLQuery(Subscription))
                   .use: stream =>
                     for
                       fiber <- stream.compile.toList.start
                       _     <- backend.awaitSubscribeId()
                       _     <- backend.failSends(true)
                     yield fiber
      // The resource release stops the subscription, and the send of the complete message fails.
      done    <- reader.join.as(true).timeoutTo(Timeout, IO.pure(false))
    yield assert(done, "The subscription stream did not end.")

  // The acquisition fails, so the resource release never runs and cannot do the cleanup.
  test("A subscription whose subscribe message fails is not restarted on a reconnection"):
    for
      backend    <- TestWebSocketBackend[IO]()
      client     <- clientOn(backend, retryOnce)
      _          <- backend.failSends(true)
      result     <- client.subscribeInternal[Json](GraphQLQuery(Subscription)).use_.attempt
      _          <- backend.failSends(false)
      _          <- backend.closeFromServer(CloseParams(1006, "retry").asRight)
      _          <- backend.awaitConnectionInits(2).timeout(Timeout)
      _          <- IO.sleep(Settle)
      subscribes <- backend.subscribes
    yield
      assert(result.isLeft, "The subscription did not fail.")
      assertEquals(subscribes, Nil)

  test("A subscription released after a disconnection logs no error"):
    for
      backend          <- TestWebSocketBackend[IO]()
      (client, logger) <- clientWithLogger(backend)
      _                <- client
                            .subscribeInternal[Json](GraphQLQuery(Subscription))
                            .use: stream =>
                              for
                                fiber <- stream.compile.toList.attempt.start
                                _     <- backend.awaitSubscribeId()
                                _     <- backend.closeFromServer(CloseParams(1006, "gone").asRight)
                                _     <- fiber.joinWithNever.timeout(Timeout)
                              yield ()
      logged           <- errors(logger)
    yield assertEquals(logged, Vector.empty)
