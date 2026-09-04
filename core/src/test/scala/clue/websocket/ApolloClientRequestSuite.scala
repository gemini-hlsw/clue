// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.websocket

import cats.effect.*
import cats.syntax.all.*
import clue.PersistentClientStatus
import clue.model.GraphQLQuery
import clue.model.GraphQLResponse
import clue.model.StreamingMessage
import io.circe.Json

import java.util.concurrent.TimeoutException
import scala.concurrent.duration.*

/**
 * Tests for a one-shot request over a WebSocket. The caller must be able to cancel the request, and
 * the client must clean up afterwards.
 */
final class ApolloClientRequestSuite extends ApolloClientSuite:

  private val Query  = "query { x }"
  private val Answer = Json.fromInt(7)

  /** Starts a request and waits until the client sent the subscribe message for it. */
  private def startedRequest(
    backend: TestWebSocketBackend[IO],
    client:  ApolloClient[IO, String, Unit]
  ): IO[(FiberIO[GraphQLResponse[Json]], String)] =
    for
      fiber <- client.requestInternal[Json](GraphQLQuery(Query)).start
      id    <- backend.awaitSubscribeId()
    yield (fiber, id)

  /** Starts a request and answers it from the server. */
  private def answeredRequest(
    backend: TestWebSocketBackend[IO],
    client:  ApolloClient[IO, String, Unit]
  ): IO[(GraphQLResponse[Json], String)] =
    for
      (fiber, id) <- startedRequest(backend, client)
      _           <- backend.emitNext(id, Answer)
      response    <- fiber.joinWithNever.timeout(Timeout)
    yield (response, id)

  test("A request on a silent server honors a timeout"):
    for
      backend <- TestWebSocketBackend[IO]()
      client  <- clientOn(backend)
      outcome <- client
                   .requestInternal[Json](GraphQLQuery(Query))
                   .timeout(200.millis)
                   .attempt
    yield assert(outcome.swap.exists(_.isInstanceOf[TimeoutException]), clue = outcome)

  test("A request started while the client connects honors a timeout"):
    for
      backend <- TestWebSocketBackend[IO](autoAck = false)
      client  <- clientOn(backend, connect = false)
      _       <- client.connect().start
      _       <- backend.awaitConnectionInits(1).timeout(Timeout)
      outcome <- client
                   .requestInternal[Json](GraphQLQuery(Query))
                   .timeout(200.millis)
                   .attempt
    yield assert(outcome.swap.exists(_.isInstanceOf[TimeoutException]), clue = outcome)

  test("A canceled request sends a complete message for its id"):
    for
      backend   <- TestWebSocketBackend[IO]()
      client    <- clientOn(backend)
      (f, id)   <- startedRequest(backend, client)
      _         <- f.cancel
      completes <- backend.completes
    yield assertEquals(completes, List(id))

  test("A canceled request leaves no subscription for a reconnection to restart"):
    for
      backend <- TestWebSocketBackend[IO]()
      client  <- clientOn(backend, retryOnce)
      (f, _)  <- startedRequest(backend, client)
      _       <- f.cancel
      _       <- backend.closeFromServer(CloseParams(1000, "retry").asRight)
      // The second connection_init proves that the client reconnected.
      _       <- backend.awaitConnectionInits(2).timeout(Timeout)
      after   <- backend.subscribes
    yield assertEquals(after.length, 1)

  /** Drives the client into a reconnection that never completes, with the request still pending. */
  private def reconnecting(
    backend: TestWebSocketBackend[IO],
    client:  ApolloClient[IO, String, Unit]
  ): IO[FiberIO[GraphQLResponse[Json]]] =
    for
      (f, _) <- startedRequest(backend, client)
      _      <- backend.autoAck(false)
      // The client reconnects inside `onClose` and waits there for an ack that never arrives, so
      // the close runs in its own fiber.
      _      <- backend.closeFromServer(CloseParams(1000, "retry").asRight).start
      // The second connection_init proves that the client reconnects.
      _      <- backend.awaitConnectionInits(2).timeout(Timeout)
    yield f

  test("A request canceled while the client reconnects returns"):
    for
      backend  <- TestWebSocketBackend[IO]()
      client   <- clientOn(backend, retryOnce)
      f        <- reconnecting(backend, client)
      canceler <- f.cancel.start
      done     <- canceler.join.as(true).timeoutTo(Timeout, IO.pure(false))
    yield assert(done, "The cancelation did not return.")

  test("A request canceled while the client reconnects leaves no subscription to restart"):
    for
      backend <- TestWebSocketBackend[IO]()
      client  <- clientOn(backend, retryOnce)
      f       <- reconnecting(backend, client)
      _       <- f.cancel.timeout(Timeout)
      _       <- backend.emit(StreamingMessage.FromServer.ConnectionAck())
      _       <- awaitStatus(client, PersistentClientStatus.Connected).timeout(Timeout)
      _       <- IO.sleep(Settle)
      after   <- backend.subscribes
    yield assertEquals(after.length, 1)

  test("A request that the server answers logs no error"):
    for
      backend          <- TestWebSocketBackend[IO]()
      (client, logger) <- clientWithLogger(backend)
      _                <- answeredRequest(backend, client)
      logged           <- errors(logger)
    yield assertEquals(logged, Vector.empty)

  // The protocol permits a complete message for an operation that the server already completed, so
  // the client always sends one.
  test("A request that the server answers sends a complete message"):
    for
      backend   <- TestWebSocketBackend[IO]()
      client    <- clientOn(backend)
      (_, id)   <- answeredRequest(backend, client)
      completes <- backend.completes
    yield assertEquals(completes, List(id))

  // The caller already holds the response, so a dead socket must not turn it into a failure.
  test("A request whose complete message fails still returns the response"):
    for
      backend          <- TestWebSocketBackend[IO]()
      (client, logger) <- clientWithLogger(backend)
      (f, id)          <- startedRequest(backend, client)
      _                <- backend.failSends(true)
      _                <- backend.emitNext(id, Answer)
      result           <- f.joinWithNever.timeout(Timeout)
      logged           <- errors(logger)
    yield
      assertEquals(result.data, Answer.some)
      assert(logged.exists(_.contains("Error releasing subscription")), clue = logged)

  test("A request returns the first response from the server"):
    for
      backend     <- TestWebSocketBackend[IO]()
      client      <- clientOn(backend)
      (result, _) <- answeredRequest(backend, client)
    yield assertEquals(result.data, Answer.some)
