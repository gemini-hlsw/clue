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
 */
final class TestWebSocketBackend[F[_]: Async] private (
  sentRef:    Ref[F, Vector[StreamingMessage.FromClient]],
  currentRef: Ref[F, Option[(PersistentBackendHandler[F, CloseEvent], ConnectionId)]],
  closesRef:  Ref[F, Vector[Option[CloseParams]]],
  autoAckRef: Ref[F, Boolean]
) extends WebSocketBackend[F, String]:

  /** Turns the automatic `connection_ack` answer on or off. */
  def autoAck(enabled: Boolean): F[Unit] = autoAckRef.set(enabled)

  /** The messages that the client sent, in order. */
  val sent: F[List[StreamingMessage.FromClient]] = sentRef.get.map(_.toList)

  /** The close parameters of every `closeInternal` call, in order. */
  val closes: F[List[Option[CloseParams]]] = closesRef.get.map(_.toList)

  /** Waits until the recorded client messages match the condition. */
  def awaitSent(condition: List[StreamingMessage.FromClient] => Boolean): F[Unit] =
    sent.flatMap: msgs =>
      if condition(msgs) then Async[F].unit else Async[F].cede >> awaitSent(condition)

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
                case StreamingMessage.FromClient.ConnectionInit(_) =>
                  autoAckRef.get.flatMap: enabled =>
                    if enabled then
                      handler.onMessage(
                        connectionId,
                        StreamingMessage.FromServer.ConnectionAck().asJson.noSpaces
                      )
                    else Async[F].unit
                case _                                             => Async[F].unit)

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
      ack    <- Ref.of[F, Boolean](autoAck)
    yield new TestWebSocketBackend(sent, cur, closes, ack)
