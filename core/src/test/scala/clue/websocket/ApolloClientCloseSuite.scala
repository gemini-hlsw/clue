// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.websocket

import cats.effect.*
import cats.syntax.all.*
import clue.DisconnectedException
import clue.PersistentClientStatus
import clue.RemoteInitializationException
import clue.model.GraphQLQuery
import clue.model.GraphQLResponse
import clue.model.StreamingMessage
import io.circe.Json

/**
 * Tests for the paths that end a connection. Every subscription must terminate. It must not wait
 * forever.
 */
final class ApolloClientCloseSuite extends ApolloClientSuite:

  private def subscription(
    client: ApolloClient[IO, String, Unit]
  ): Resource[IO, fs2.Stream[IO, GraphQLResponse[Json]]] =
    client.subscribeInternal[Json](GraphQLQuery("subscription { x }"))

  test("A close without a reconnection strategy terminates the subscription"):
    for
      backend <- TestWebSocketBackend[IO]()
      client  <- clientOn(backend)
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

  // The client status now comes from the client state, which changes more often than the status.
  test("A reconnection reports each client status once"):
    for
      backend  <- TestWebSocketBackend[IO]()
      client   <- clientOn(backend, retryOnce)
      statuses <- Ref.of[IO, List[PersistentClientStatus]](Nil)
      watcher  <- client.statusStream.evalTap(s => statuses.update(_ :+ s)).compile.drain.start
      // The watcher reads the current status first.
      _        <- IO.sleep(Settle)
      // The reconnection holds in the Connecting state until the test acknowledges it.
      _        <- backend.autoAck(false)
      // The reconnection runs inside the close handler, which waits for the acknowledgement.
      _        <- backend.closeFromServer(CloseParams(1000, "retry").asRight).start
      _        <- backend.awaitConnectionInits(2).timeout(Timeout)
      _        <- awaitStatus(client, PersistentClientStatus.Connecting).timeout(Timeout)
      _        <- backend.emit(StreamingMessage.FromServer.ConnectionAck())
      _        <- awaitStatus(client, PersistentClientStatus.Connected).timeout(Timeout)
      _        <- IO.sleep(Settle)
      _        <- watcher.cancel
      seen     <- statuses.get
    yield assertEquals(
      seen,
      List(
        PersistentClientStatus.Connected,
        PersistentClientStatus.Connecting,
        PersistentClientStatus.Connected
      )
    )

  test("A close while connecting terminates the subscription"):
    // The strategy reconnects once, on the close with reason "retry". The second close
    // then finds the client in the Connecting state and gives up.
    for
      backend <- TestWebSocketBackend[IO]()
      client  <- clientOn(backend, retryOnce)
      outcome <- subscription(client).use: stream =>
                   for
                     fiber <- stream.compile.toList.attempt.start
                     // The reconnection must not complete, so that the client stays Connecting.
                     _     <- backend.autoAck(false)
                     _     <- backend.closeFromServer(CloseParams(1000, "retry").asRight).start
                     // The second connection_init proves that the client reconnects.
                     _     <- backend.awaitConnectionInits(2).timeout(Timeout)
                     _     <- backend.closeFromServer(CloseParams(4000, "gone").asRight)
                     r     <- fiber.joinWithNever.timeout(Timeout)
                   yield r
    yield assertEquals(outcome, DisconnectedException("4000: gone").asLeft)

  test("A disconnection while the client reconnects terminates the subscription"):
    for
      backend <- TestWebSocketBackend[IO]()
      client  <- clientOn(backend, retryOnce)
      reader  <- subscription(client).use: stream =>
                   for
                     fiber <- stream.compile.toList.attempt.start
                     _     <- backend.awaitSubscribeId()
                     // The reconnection must not complete, so that the client stays Connecting.
                     _     <- backend.autoAck(false)
                     _     <- backend.closeFromServer(CloseParams(1000, "retry").asRight).start
                     _     <- backend.awaitConnectionInits(2).timeout(Timeout)
                     _     <- client.disconnect()
                   yield fiber
      done    <- reader.join.as(true).timeoutTo(Timeout, IO.pure(false))
    yield assert(done, "The subscription stream did not end.")

  test("A connect() call fails when the reconnection gives up"):
    for
      backend <- TestWebSocketBackend[IO](autoAck = false)
      client  <- clientOn(backend, retryOnce, connect = false)
      // The caller waits for the acknowledgement of the first connection.
      fiber   <- client.connect().attempt.start
      _       <- backend.awaitConnectionInits(1).timeout(Timeout)
      // The reconnection cannot open a socket. The strategy gives up on a connect error.
      _       <- backend.failConnects(true)
      _       <- backend.closeFromServer(CloseParams(1000, "retry").asRight).start
      outcome <- fiber.joinWithNever.timeout(Timeout)
    yield assertEquals(outcome.leftMap(_.getMessage), "connect failed".asLeft)

  test("A close while initializing fails connect() with a RemoteInitializationException"):
    for
      backend <- TestWebSocketBackend[IO](autoAck = false)
      client  <- clientOn(backend, connect = false)
      fiber   <- client.connect().attempt.start
      _       <- backend.awaitConnectionInits(1).timeout(Timeout)
      _       <- backend.closeFromServer(CloseParams(4403, "Forbidden").asRight)
      outcome <- fiber.joinWithNever.timeout(Timeout)
    yield assertEquals(
      outcome,
      RemoteInitializationException(DisconnectedException("4403: Forbidden"))
        .asLeft[io.circe.JsonObject]
    )

  test("A subscription that waits for a connection fails with the cause of the close"):
    for
      backend <- TestWebSocketBackend[IO](autoAck = false)
      client  <- clientOn(backend, connect = false)
      _       <- client.connect().start
      _       <- backend.awaitConnectionInits(1).timeout(Timeout)
      // The client is Connecting, so the subscription waits for the connection.
      fiber   <- subscription(client).use_.attempt.start
      _       <- backend.closeFromServer(CloseParams(1006, "gone").asRight)
      outcome <- fiber.joinWithNever.timeout(Timeout)
    yield assertEquals(outcome, DisconnectedException("1006: gone").asLeft)
