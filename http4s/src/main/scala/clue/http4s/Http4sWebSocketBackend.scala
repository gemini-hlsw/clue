// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.http4s

import cats.effect.*
import cats.effect.Resource.ExitCase
import cats.effect.implicits.*
import cats.syntax.all.*
import clue.*
import clue.model.StreamingMessage
import clue.model.json.given
import clue.websocket.*
import io.circe.syntax.*
import org.http4s.Headers
import org.http4s.Uri
import org.http4s.client.websocket.*

/**
 * Streaming backend for http4s WebSocket client.
 */
final class Http4sWebSocketBackend[F[_]: Concurrent](client: WSClient[F])
    extends WebSocketBackend[F, Uri]:

  override def connect(
    uri:          Uri,
    handler:      WebSocketHandler[F],
    connectionId: ConnectionId
  ): F[WebSocketConnection[F]] =
    for
      (connection, release) <-
        client
          .connectHighLevel:
            WSRequest(uri).withHeaders(Headers("Sec-WebSocket-Protocol" -> "graphql-transport-ws"))
          .allocated
      // Refs to ensure release/close are only executed once
      releaseState          <- Ref.of[F, Boolean](false)
      closeState            <- Ref.of[F, Boolean](false)
      releaseNow             =
        releaseState
          .modify(released => true -> !released)
          .flatMap(isNotReleased => release.whenA(isNotReleased))
      notifyClose            =
        (event: CloseEvent) =>
          closeState
            .modify(closed => true -> !closed)
            .flatMap(isNotClosed => handler.onClose(connectionId, event).whenA(isNotClosed))
      canceledClose          =
        notifyClose(new GraphQLException(s"WS listener canceled. URI: [$uri]").asLeft)
      listener              <-
        connection.receiveStream
          .evalTap:
            case WSFrame.Text(data, _) => handler.onMessage(connectionId, data)
            case WSFrame.Binary(_, _)  => Concurrent[F].unit
          .onFinalizeCase:
            case ExitCase.Succeeded  =>
              connection.closeFrame.tryGet.flatMap: closeFrame =>
                val event = closeFrame
                  .map(close => CloseParams(close.statusCode, close.reason))
                  .toRight:
                    new GraphQLException(
                      s"Unexpected clean close for WS without close frame. URI: [$uri]"
                    )
                notifyClose(event) >> releaseNow
            case ExitCase.Errored(t) => notifyClose(t.asLeft) >> releaseNow
            case ExitCase.Canceled   => canceledClose >> releaseNow
          .compile
          .drain
          .start
    yield new Http4sWSConnection(connection, listener, canceledClose, releaseNow)

object Http4sWebSocketBackend:
  def apply[F[_]: Concurrent](client: WSClient[F]): Http4sWebSocketBackend[F] =
    new Http4sWebSocketBackend(client)

final class Http4sWSConnection[F[_]: Concurrent](
  private val conn:       WSConnectionHighLevel[F],
  private val listener:   Fiber[F, Throwable, Unit],
  private val onCanceled: F[Unit],
  private val releaseNow: F[Unit]
) extends WebSocketConnection[F]:
  override def send(msg: StreamingMessage.FromClient): F[Unit] =
    conn.send(WSFrame.Text(msg.asJson.noSpaces))

  override def closeInternal(closeParameters: Option[CloseParams]): F[Unit] =
    listener.cancel >> onCanceled >> releaseNow
