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

// This implementation follows the Apollo protocol, specified in:
// https://github.com/apollographql/subscriptions-transport-ws/blob/master/PROTOCOL.md
// Also see: https://medium.com/@rob.blackbourn/writing-a-graphql-websocket-subscriber-in-javascript-4451abb9cd60
final class WebSocketJSConnection[F[_]: Sync: Logger](private val ws: WebSocket)
    extends BackendConnection[F] {
  override def send(msg: StreamingMessage.FromClient): F[Unit] =
    Sync[F].delay(ws.send(msg.asJson.toString))

  override def close(): F[Unit] =
    Logger[F].trace("Disconnecting WebSocket...") >>
      Sync[F].delay(ws.close())
}

final class WebSocketJSBackend[F[_]: ConcurrentEffect: Logger] extends StreamingBackend[F] {
  private val Protocol = "graphql-ws"

  override def connect(
    uri:       Uri,
    onMessage: String => F[Unit],
    onError:   Throwable => F[Unit],
    onClose:   Boolean => F[Unit]
  ): F[BackendConnection[F]] =
    Async[F].async { cb =>
      val ws = new WebSocket(uri.toString, Protocol)

      ws.onopen = { _: Event =>
        Logger[F].trace("WebSocket open").toIO.unsafeRunAsyncAndForget()
        cb(Right(new WebSocketJSConnection(ws)))
      }

      ws.onmessage = { e: MessageEvent =>
        (e.data match {
          case str: String => onMessage(str)
          case other       =>
            Logger[F].error(s"Unexpected event from WebSocket for [$uri]: [$other]")
        }).toIO.unsafeRunAsyncAndForget()
      }

      ws.onerror = { e: Event =>
        Logger[F].error(s"Error on WebSocket for [$uri]: $e")
        onError(new GraphQLException(e.toString)).toIO.unsafeRunAsyncAndForget()
      }

      ws.onclose = { e: CloseEvent =>
        (
          Logger[F].trace("WebSocket closed") >>
            onClose(e.wasClean)
        ).toIO.unsafeRunAsyncAndForget()
      }
    }
}

object WebSocketJSBackend {
  def apply[F[_]: ConcurrentEffect: Logger]: WebSocketJSBackend[F] = new WebSocketJSBackend[F]
}
