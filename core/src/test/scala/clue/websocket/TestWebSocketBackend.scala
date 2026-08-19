// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.websocket

import cats.effect.*
import cats.syntax.all.*
import clue.ConnectionId
import clue.PersistentBackendHandler
import clue.PersistentConnection
import clue.model.StreamingMessage
import clue.model.json.given
import io.circe.syntax.*

/**
 * In-memory WebSocket backend for tests. The test drives the client through it. It records the
 * messages that the client sends. It feeds server messages and close events to the client.
 *
 * @param autoAck
 *   if true, the backend answers a `connection_init` with a `connection_ack`
 */
final class TestWebSocketBackend[F[_]: Async] private (
  sentRef:    Ref[F, Vector[StreamingMessage.FromClient]],
  currentRef: Ref[F, Option[(PersistentBackendHandler[F, CloseEvent], ConnectionId)]],
  closesRef:  Ref[F, Vector[Option[CloseParams]]],
  autoAck:    Boolean
) extends WebSocketBackend[F, String]:

  /** The messages that the client sent, in order. */
  val sent: F[List[StreamingMessage.FromClient]] = sentRef.get.map(_.toList)

  /** The close parameters of every `closeInternal` call, in order. */
  val closes: F[List[Option[CloseParams]]] = closesRef.get.map(_.toList)

  /** Sends a message from the server to the client. */
  def emit(msg: StreamingMessage.FromServer): F[Unit] = emitRaw(msg.asJson.noSpaces)

  /** Sends raw text from the server to the client. */
  def emitRaw(msg: String): F[Unit] =
    currentRef.get.flatMap(_.traverse_((handler, id) => handler.onMessage(id, msg)))

  /** Closes the connection from the server side. */
  def closeFromServer(event: CloseEvent): F[Unit] =
    currentRef.get.flatMap(_.traverse_((handler, id) => handler.onClose(id, event)))

  override def connect(
    connectionParams: String,
    handler:          PersistentBackendHandler[F, CloseEvent],
    connectionId:     ConnectionId
  ): F[WebSocketConnection[F]] =
    currentRef
      .set((handler, connectionId).some)
      .as(
        new PersistentConnection[F, CloseParams]:
          override def send(msg: StreamingMessage.FromClient): F[Unit] =
            sentRef.update(_ :+ msg) >>
              (msg match
                case StreamingMessage.FromClient.ConnectionInit(_) if autoAck =>
                  handler.onMessage(
                    connectionId,
                    StreamingMessage.FromServer.ConnectionAck().asJson.noSpaces
                  )
                case _                                                        => Async[F].unit)

          override protected[clue] def closeInternal(
            closeParameters: Option[CloseParams]
          ): F[Unit] =
            closesRef.update(_ :+ closeParameters)
      )

object TestWebSocketBackend:
  def apply[F[_]: Async](autoAck: Boolean = true): F[TestWebSocketBackend[F]] =
    for
      sent   <- Ref.of[F, Vector[StreamingMessage.FromClient]](Vector.empty)
      cur    <- Ref.of[F, Option[(PersistentBackendHandler[F, CloseEvent], ConnectionId)]](none)
      closes <- Ref.of[F, Vector[Option[CloseParams]]](Vector.empty)
    yield new TestWebSocketBackend(sent, cur, closes, autoAck)
