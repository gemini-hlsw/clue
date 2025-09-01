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
    client
      .connectHighLevel:
        WSRequest(uri).withHeaders(Headers("Sec-WebSocket-Protocol" -> "graphql-ws"))
      .allocated // TODO replace with allocatedCase
      .flatMap: (connection, release) =>
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
                handler.onClose(connectionId, event) >> release // TODO
            case ExitCase.Errored(t) => handler.onClose(connectionId, t.asLeft) >> release // TODO
            case ExitCase.Canceled   =>
              handler.onClose(
                connectionId,
                new GraphQLException(s"WS listener canceled. URI: [$uri]").asLeft
              ) >> release // TODO
          .compile
          .drain
          .start
          .as(new Http4sWSConnection(connection))

object Http4sWebSocketBackend:
  def apply[F[_]: Concurrent](client: WSClient[F]): Http4sWebSocketBackend[F] =
    new Http4sWebSocketBackend(client)

final class Http4sWSConnection[F[_]: Concurrent](
  private val conn: WSConnectionHighLevel[F]
) extends WebSocketConnection[F]:
  override def send(msg: StreamingMessage.FromClient): F[Unit] =
    conn.send(WSFrame.Text(msg.asJson.toString))

  // In high-level WS, we cannot specify close code.
  override def closeInternal(closeParameters: Option[CloseParams]): F[Unit] =
    Concurrent[F].unit // TODO
