// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.websocket

import cats.data.Ior
import cats.effect.*
import cats.syntax.all.*
import clue.ConnectionId
import clue.PersistentBackendHandler
import clue.PersistentConnection
import clue.model.GraphQLResponse
import clue.model.StreamingMessage
import clue.model.json.given
import fs2.concurrent.SignallingRef
import io.circe.Json
import io.circe.syntax.*

/**
 * In-memory WebSocket backend for tests. The test drives the client through it. It records the
 * messages that the client sends. It feeds server messages and close events to the client.
 */
final class TestWebSocketBackend[F[_]: Async] private (
  sentRef:         SignallingRef[F, Vector[StreamingMessage.FromClient]],
  currentRef:      Ref[F, Option[(PersistentBackendHandler[F, CloseEvent], ConnectionId)]],
  closesRef:       SignallingRef[F, Vector[Option[CloseParams]]],
  autoAckRef:      Ref[F, Boolean],
  failSendsRef:    Ref[F, Boolean],
  failConnectsRef: Ref[F, Boolean],
  gatesRef:        Ref[F, List[Deferred[F, Unit]]]
) extends WebSocketBackend[F, String]:

  /** Turns the automatic `connection_ack` answer on or off. */
  def autoAck(enabled: Boolean): F[Unit] = autoAckRef.set(enabled)

  /**
   * Makes the first `count` connections wait for returned gates. Gate at index `i` belongs to
   * connection id `i`.
   */
  def gateConnects(count: Int): F[List[Deferred[F, Unit]]] =
    List.fill(count)(Deferred[F, Unit]).sequence.flatTap(gatesRef.set)

  /** Waits for the gate of this connection, if the test set one. */
  private def awaitGate(connectionId: ConnectionId): F[Unit] =
    gatesRef.get.flatMap(_.lift(connectionId.value).traverse_(_.get))

  /** Makes every further `send` raise, like a socket that died without a close event. */
  def failSends(enabled: Boolean): F[Unit] = failSendsRef.set(enabled)

  /** Makes every further `connect` raise, like a socket that cannot open. */
  def failConnects(enabled: Boolean): F[Unit] = failConnectsRef.set(enabled)

  /** The messages that the client sent, in order. */
  val sent: F[List[StreamingMessage.FromClient]] = sentRef.get.map(_.toList)

  /** The close parameters of every `closeInternal` call, in order. */
  val closes: F[List[Option[CloseParams]]] = closesRef.get.map(_.toList)

  /** Waits until the client closed at least `count` connections. */
  def awaitCloses(count: Int): F[Unit] =
    closesRef.discrete.find(_.sizeIs >= count).compile.drain

  /** The subscribe messages that the client sent, in order. */
  val subscribes: F[List[StreamingMessage.FromClient.Subscribe]] =
    sent.map(_.collect { case s: StreamingMessage.FromClient.Subscribe => s })

  /** The subscription ids of the complete messages that the client sent, in order. */
  val completes: F[List[String]] =
    sent.map(_.collect { case StreamingMessage.FromClient.Complete(id) => id })

  /** Waits until the recorded client messages produce a result, and returns it. */
  private def awaitSentF[A](f: List[StreamingMessage.FromClient] => Option[A]): F[A] =
    sentRef.discrete.map(msgs => f(msgs.toList)).unNone.head.compile.lastOrError

  /** Waits for the first subscribe message, for `query` if given, and returns its id. */
  def awaitSubscribeId(query: Option[String] = none): F[String] =
    awaitSentF(_.collectFirst {
      case s: StreamingMessage.FromClient.Subscribe if query.forall(_ === s.payload.query.value) =>
        s.id
    })

  /** Waits until the recorded client messages match the condition. */
  private def awaitSent(condition: List[StreamingMessage.FromClient] => Boolean): F[Unit] =
    awaitSentF(msgs => Option.when(condition(msgs))(()))

  /** Waits until the client sent at least `count` connection_init messages. */
  def awaitConnectionInits(count: Int): F[Unit] =
    awaitSent(_.count(_.isInstanceOf[StreamingMessage.FromClient.ConnectionInit]) >= count)

  /** Sends a message from the server to the client. */
  def emit(msg: StreamingMessage.FromServer): F[Unit] = emitRaw(msg.asJson.noSpaces)

  /** Sends a data message from the server for the subscription id. */
  def emitNext(id: String, data: Json): F[Unit] =
    emit(StreamingMessage.FromServer.Next(id, GraphQLResponse(Ior.right(data))))

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
    (awaitGate(connectionId) >>
      failConnectsRef.get.ifM(
        Async[F].raiseError(new RuntimeException("connect failed")),
        currentRef.set((handler, connectionId).some)
      ))
      .as(
        new PersistentConnection[F, CloseParams]:
          override def send(msg: StreamingMessage.FromClient): F[Unit] =
            failSendsRef.get.ifM(
              Async[F].raiseError(new RuntimeException("send failed")),
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
            )

          override protected[clue] def closeInternal(
            closeParameters: Option[CloseParams]
          ): F[Unit] =
            closesRef.update(_ :+ closeParameters)
      )

object TestWebSocketBackend:
  def apply[F[_]: Async](autoAck: Boolean = true): F[TestWebSocketBackend[F]] =
    for
      sent        <- SignallingRef.of[F, Vector[StreamingMessage.FromClient]](Vector.empty)
      cur         <- Ref.of[F, Option[(PersistentBackendHandler[F, CloseEvent], ConnectionId)]](none)
      closes      <- SignallingRef.of[F, Vector[Option[CloseParams]]](Vector.empty)
      ack         <- Ref.of[F, Boolean](autoAck)
      failSend    <- Ref.of[F, Boolean](false)
      failConnect <- Ref.of[F, Boolean](false)
      gates       <- Ref.of[F, List[Deferred[F, Unit]]](Nil)
    yield new TestWebSocketBackend(sent, cur, closes, ack, failSend, failConnect, gates)
