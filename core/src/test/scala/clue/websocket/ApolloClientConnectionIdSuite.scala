// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.websocket

import cats.effect.*
import cats.syntax.all.*
import clue.PersistentClientStatus
import clue.model.StreamingMessage

/**
 * Tests for the connection identity of the client state machine. A result that belongs to a
 * connection that the state replaced must not change the state.
 */
final class ApolloClientConnectionIdSuite extends ApolloClientSuite:

  test("A connect result for a replaced connection is dropped"):
    for
      backend          <- TestWebSocketBackend[IO](autoAck = false)
      gates            <- backend.gateConnects(2)
      (client, logger) <- clientWithLogger(backend, connect = false)
      // The first connect waits in the backend. The disconnect replaces the connection id.
      first            <- client.connect().attempt.start
      _                <- awaitStatus(client, PersistentClientStatus.Connecting).timeout(Timeout)
      _                <- client.disconnect()
      second           <- client.connect().start
      _                <- awaitStatus(client, PersistentClientStatus.Connecting).timeout(Timeout)
      // The first connect now returns a connection that the client must not use.
      _                <- gates.head.complete(())
      _                <- first.joinWithNever.timeout(Timeout)
      stale            <- backend.sent
      _                <- IO(assertEquals(stale, Nil, "The client used a replaced connection."))
      // The socket of the replaced connection must not leak. The client closes it in background.
      _                <- backend.awaitCloses(1).timeout(Timeout)
      closed           <- backend.closes
      _                <- IO(
                            assertEquals(closed,
                                         List(Option.empty[CloseParams]),
                                         "The client did not close the replaced socket."
                            )
                          )
      // The current connect still completes.
      _                <- gates(1).complete(())
      _                <- backend.awaitConnectionInits(1).timeout(Timeout)
      _                <- backend.emit(StreamingMessage.FromServer.ConnectionAck())
      _                <- second.joinWithNever.timeout(Timeout)
      status           <- client.status
      errs             <- errors(logger)
    yield
      assertEquals(status, PersistentClientStatus.Connected)
      assertEquals(errs, Vector.empty)

  test("An initialization result for a replaced connection is dropped"):
    for
      backend          <- TestWebSocketBackend[IO](autoAck = false)
      (client, logger) <- clientWithLogger(backend, retryOnce, connect = false)
      first            <- client.connect().start
      _                <- backend.awaitConnectionInits(1).timeout(Timeout)
      // The reconnection keeps the latch, so two initializations wait for one acknowledgement.
      _                <- backend.closeFromServer(CloseParams(1000, "retry").asRight).start
      _                <- backend.awaitConnectionInits(2).timeout(Timeout)
      _                <- backend.emit(StreamingMessage.FromServer.ConnectionAck())
      _                <- awaitStatus(client, PersistentClientStatus.Connected).timeout(Timeout)
      _                <- first.joinWithNever.timeout(Timeout)
      errs             <- errors(logger)
    yield assertEquals(errs,
                       Vector.empty,
                       "The client reported an error for a replaced connection."
    )
