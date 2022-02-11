// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.http4sjdk

import cats.effect.Resource.ExitCase
import cats.effect._
import cats.effect.implicits._
import cats.syntax.all._
import clue._
import clue.model.StreamingMessage
import clue.model.json._
import io.circe.syntax._
import org.http4s.Headers
import org.http4s.Uri
import org.http4s.jdkhttpclient._

import java.net.http.HttpClient

/**
 * Streaming backend for http4s-JDK WebSocket.
 */
final class Http4sJDKWSBackend[F[_]: Async](client: WSClient[F]) extends WebSocketBackend[F] {

  override def connect(
    uri:          Uri,
    handler:      PersistentBackendHandler[F, WebSocketCloseEvent],
    connectionId: ConnectionId
  ): F[PersistentConnection[F, WebSocketCloseParams]] =
    client
      .connectHighLevel(
        WSRequest(uri, headers = Headers("Sec-WebSocket-Protocol" -> "graphql-ws"))
      )
      .allocated
      .flatMap { case (connection, release) =>
        connection.receiveStream
          .evalTap {
            case WSFrame.Text(data, _) => handler.onMessage(connectionId, data)
            case WSFrame.Binary(_, _)  => Async[F].unit
          }
          .onFinalizeCase {
            case ExitCase.Succeeded  =>
              connection.closeFrame.tryGet.flatMap { closeFrame =>
                val event = closeFrame
                  .map(close => WebSocketCloseParams(close.statusCode, close.reason))
                  .toRight(
                    new GraphQLException(
                      s"Unexpected clean close for WS without close frame. URI: [$uri]"
                    )
                  )
                handler.onClose(connectionId, event) >> release
              }
            case ExitCase.Errored(t) => handler.onClose(connectionId, t.asLeft) >> release
            case ExitCase.Canceled   =>
              connection.sendClose() >>
                handler.onClose(connectionId,
                                new GraphQLException(s"WS listener canceled. URI: [$uri]").asLeft
                ) >> release
          }
          .compile
          .drain
          .start
          .as(new Http4sJDKWSConnection(connection))
      }
}

object Http4sJDKWSBackend {
  def apply[F[_]: Async]: Resource[F, Http4sJDKWSBackend[F]] =
    JdkWSClient.simple[F].map(new Http4sJDKWSBackend(_))

  def fromHttpClient[F[_]: Async](client: HttpClient): Resource[F, Http4sJDKWSBackend[F]] =
    JdkWSClient[F](client).map(new Http4sJDKWSBackend(_))
}

final class Http4sJDKWSConnection[F[_] /*: Sync: Logger*/ ](
  private val conn: WSConnectionHighLevel[F]
) extends WebSocketConnection[F] {
  override def send(msg: StreamingMessage.FromClient): F[Unit] =
    conn.send(WSFrame.Text(msg.asJson.toString))

  // In high-level WS, we cannot specify close code.
  override def closeInternal(closeParameters: Option[WebSocketCloseParams]): F[Unit] = {
    val params = closeParameters.getOrElse(WebSocketCloseParams())
    conn.sendClose(params.reason.orEmpty)
  }
}
