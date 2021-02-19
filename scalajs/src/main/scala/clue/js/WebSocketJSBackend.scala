// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.js

import cats.syntax.all._
import cats.effect._
import cats.effect.implicits._
import clue._
import clue.model.StreamingMessage
import clue.model.json._
import io.chrisdavenport.log4cats.Logger
import io.circe.syntax._
import org.scalajs.dom.raw.CloseEvent
import org.scalajs.dom.raw.Event
import org.scalajs.dom.raw.MessageEvent
import org.scalajs.dom.raw.WebSocket
import sttp.model.Uri
import cats.effect.concurrent.Ref

/**
 * Streaming backend for JS WebSocket.
 */
final class WebSocketJSBackend[F[_]: Effect: Logger] extends WebSocketBackend[F] {
  private val Protocol = "graphql-ws"

  override def connect(
    uri:       Uri,
    onMessage: String => F[Unit],
    onClose:   WebSocketCloseEvent => F[Unit]
  ): F[PersistentConnection[F, WebSocketCloseParams]] =
    for {
      isOpen     <- Ref[F].of(false)
      isErrored  <- Ref[F].of(false)
      connection <-
        Async[F].async[PersistentConnection[F, WebSocketCloseParams]] { cb =>
          val ws = new WebSocket(uri.toString, Protocol)

          ws.onopen = { _: Event =>
            (
              for {
                _ <- isOpen.set(true)
                _ <- Logger[F].trace("WebSocket open")
              } yield cb(new WebSocketJSConnection(ws).asRight)
            ).uncancelable
              .runAsync(_ => IO.unit)
              .unsafeRunSync()
          }

          // TODO PROCESS ERRORS/INTERRUPTIONS ON CALLBACKS !

          ws.onmessage = { e: MessageEvent =>
            (e.data match {
              case str: String => onMessage(str)
              case other       =>
                Logger[F].error(s"Unexpected event from WebSocket for [$uri]: [$other]")
            }).runAsync(_ => IO.unit).unsafeRunSync()
          }

          // According to spec, onError is only closed prior to a close.
          // https://html.spec.whatwg.org/multipage/web-sockets.html
          ws.onerror = { _: Event =>
            (
              for {
                _    <- Logger[F].error(s"Error on WebSocket for [$uri]")
                _    <- isErrored.set(true)
                open <- isOpen.get
              } yield if (!open) cb(new ConnectionException().asLeft)
            ).uncancelable
              .runAsync(_ => IO.unit)
              .unsafeRunSync()
          }

          ws.onclose = { e: CloseEvent =>
            (
              for {
                _       <- Logger[F].trace("WebSocket closed")
                errored <- isErrored.get
                _       <- onClose(WebSocketCloseEvent(e.code, e.reason, e.wasClean, errored))
              } yield ()
            ).runAsync(_ => IO.unit).unsafeRunSync()
          }
        }
    } yield connection
}

object WebSocketJSBackend {
  def apply[F[_]: Effect: Logger]: WebSocketJSBackend[F] = new WebSocketJSBackend[F]
}

final class WebSocketJSConnection[F[_]: Sync: Logger](private val ws: WebSocket)
    extends WebSocketConnection[F] {
  override def send(msg: StreamingMessage.FromClient): F[Unit] =
    Sync[F].delay(ws.send(msg.asJson.toString))

  override def closeInternal(closeParameters: Option[WebSocketCloseParams]): F[Unit] =
    Logger[F].trace("Disconnecting WebSocket...") >>
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
