// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.js

import cats.syntax.all._
import cats.effect._
import cats.effect.implicits._
import clue._
import clue.model.StreamingMessage
import clue.model.json._
import org.typelevel.log4cats.Logger
import io.circe.syntax._
import org.scalajs.dom.raw.CloseEvent
import org.scalajs.dom.raw.Event
import org.scalajs.dom.raw.MessageEvent
import org.scalajs.dom.raw.WebSocket
import sttp.model.Uri
import cats.effect.Ref
import cats.effect.std.Dispatcher

/**
 * Streaming backend for JS WebSocket.
 */
final class WebSocketJSBackend[F[_]: Async: Logger](dispatcher: Dispatcher[F])
    extends WebSocketBackend[F] {
  private val Protocol = "graphql-ws"

  override def connect(
    uri:          Uri,
    handler:      PersistentBackendHandler[F, WebSocketCloseEvent],
    connectionId: ConnectionId
  ): F[PersistentConnection[F, WebSocketCloseParams]] =
    for {
      isOpen     <- Ref[F].of(false)
      isErrored  <- Ref[F].of(false)
      connection <-
        Async[F].async_[PersistentConnection[F, WebSocketCloseParams]] { cb =>
          val ws = new WebSocket(uri.toString, Protocol)

          ws.onopen = { _: Event =>
            val open: F[Unit] = (
              for {
                _ <- isOpen.set(true)
                _ <- s"WebSocket open for URI [$uri]".traceF
              } yield cb(new WebSocketJSConnection(ws).asRight)
            ).uncancelable
            dispatcher.unsafeRunAndForget(open)
          }

          // TODO PROCESS ERRORS/INTERRUPTIONS ON CALLBACKS !

          ws.onmessage = { e: MessageEvent =>
            val message: F[Unit] = e.data match {
              case str: String => handler.onMessage(connectionId, str)
              case other       => s"Unexpected event from WebSocket for [$uri]: [$other]".errorF
            }
            dispatcher.unsafeRunAndForget(message)
          }

          // According to spec, onError is only closed prior to a close.
          // https://html.spec.whatwg.org/multipage/web-sockets.html
          ws.onerror = { _: Event =>
            val error: F[Unit] = (
              for {
                _    <- s"Error on WebSocket for [$uri]".errorF
                _    <- isErrored.set(true)
                open <- isOpen.get
              } yield if (!open) cb(new ConnectionException().asLeft)
            ).uncancelable
            dispatcher.unsafeRunAndForget(error)
          }

          ws.onclose = { e: CloseEvent =>
            val close: F[Unit] =
              for {
                _       <- s"WebSocket closed for URI [$uri]".traceF
                errored <- isErrored.get
                _       <- handler.onClose(connectionId,
                                           if (errored) new DisconnectedException().asLeft
                                           else WebSocketCloseParams(e.code, e.reason).asRight
                           )
              } yield ()
            dispatcher.unsafeRunAndForget(close)
          }
        }
    } yield connection
}

object WebSocketJSBackend {
  def apply[F[_]: Async: Logger](dispatcher: Dispatcher[F]): WebSocketJSBackend[F] =
    new WebSocketJSBackend[F](dispatcher)
}

final class WebSocketJSConnection[F[_]: Sync: Logger](private val ws: WebSocket)
    extends WebSocketConnection[F] {
  override def send(msg: StreamingMessage.FromClient): F[Unit] =
    Sync[F].delay(ws.send(msg.asJson.toString))

  override def closeInternal(closeParameters: Option[WebSocketCloseParams]): F[Unit] =
    "Disconnecting WebSocket...".traceF >>
      Sync[F].delay {
        val params = closeParameters.getOrElse(WebSocketCloseParams())
        // Facade for "ws.close" should define parameters as js.Undef, but it doesn't. So we contemplate all cases.
        (params.code, params.reason)
          .mapN { case (code, reason) => ws.close(code, reason) }
          .orElse(params.code.map(code => ws.close(code)))
          .orElse(params.reason.map(reason => ws.close(reason = reason)))
          .getOrElse(ws.close())
      }
}
