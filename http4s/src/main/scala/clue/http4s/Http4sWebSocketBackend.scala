// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.http4s

import cats.effect.Resource.ExitCase
import cats.effect._
import cats.effect.implicits._
import cats.syntax.all._
import clue._
import clue.model.StreamingMessage
import clue.model.json._
import clue.websocket._
import io.circe.syntax._
import org.http4s.Headers
import org.http4s.Uri
import org.http4s.client.websocket._

/**
 * Streaming backend for http4s WebSocket client.
 */
final class Http4sWebSocketBackend[F[_]: Concurrent](client: WSClient[F])
    extends WebSocketBackend[F, Uri] {

  override def connect(
    uri:          Uri,
    handler:      WebSocketHandler[F],
    connectionId: ConnectionId
  ): F[WebSocketConnection[F]] =
    client
      .connectHighLevel(
        WSRequest(uri).withHeaders(Headers("Sec-WebSocket-Protocol" -> "graphql-ws"))
        // Should we switch to "graphql-transport-ws"???
        // We should be able to specify the transport protocol.
      )
      .allocated // TODO replace with allocatedCase
      .flatMap { case (connection, release) =>
        connection.receiveStream
          .evalTap {
            case WSFrame.Text(data, _) => handler.onMessage(connectionId, data)
            case WSFrame.Binary(_, _)  => Concurrent[F].unit
          }
          .onFinalizeCase {
            case ExitCase.Succeeded  =>
              connection.closeFrame.tryGet.flatMap { closeFrame =>
                val event = closeFrame
                  .map(close => CloseParams(close.statusCode, close.reason))
                  .toRight(
                    new GraphQLException(
                      s"Unexpected clean close for WS without close frame. URI: [$uri]"
                    )
                  )
                handler.onClose(connectionId, event) >> release // TODO
              }
            case ExitCase.Errored(t) => handler.onClose(connectionId, t.asLeft) >> release // TODO
            case ExitCase.Canceled   =>
              handler.onClose(connectionId,
                              new GraphQLException(s"WS listener canceled. URI: [$uri]").asLeft
              ) >> release // TODO
          }
          .compile
          .drain
          .start
          .as(new Http4sWSConnection(connection))
      }
}

object Http4sWebSocketBackend {
  def apply[F[_]: Concurrent](client: WSClient[F]): Http4sWebSocketBackend[F] =
    new Http4sWebSocketBackend(client)
}

final class Http4sWSConnection[F[_]: Concurrent /*: Sync: Logger*/ ](
  private val conn: WSConnectionHighLevel[F]
) extends WebSocketConnection[F] {
  override def send(msg: StreamingMessage.FromClient): F[Unit] =
    conn.send(WSFrame.Text(msg.asJson.toString))

  // In high-level WS, we cannot specify close code.
  override def closeInternal(closeParameters: Option[CloseParams]): F[Unit] =
    Concurrent[F].unit // TODO
}
