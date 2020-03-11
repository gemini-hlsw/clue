package clue.js

import clue._
import cats._
import cats.effect._
import cats.effect.implicits._
import org.scalajs.dom.raw.WebSocket
import io.circe.syntax._
import io.circe.parser._
import scala.scalajs.js
import org.scalajs.dom.raw.{ CloseEvent, Event, MessageEvent }
import io.chrisdavenport.log4cats.Logger
import io.lemonlabs.uri.Url

// This implementation follows the Apollo protocol, specified in:
// https://github.com/apollographql/subscriptions-transport-ws/blob/master/PROTOCOL.md
// Also see: https://medium.com/@rob.blackbourn/writing-a-graphql-websocket-subscriber-in-javascript-4451abb9cd60
final class WebSocketJSConnection[F[_] : Sync](private val ws: WebSocket) extends BackendConnection[F] {
  override def send(msg: StreamingMessage): F[Unit] =
    Sync[F].delay(ws.send(msg.asJson.toString))

  override def close(): F[Unit] =
    Sync[F].delay(ws.close())
}

final class WebSocketJSBackend[F[_] : ConcurrentEffect : Logger] extends StreamingBackend[F] {
  private val Protocol = "graphql-ws"

  override def connect(
    url:       Url,
    onMessage: String => F[Unit],
    onError:   Throwable => F[Unit],
    onClose:   Boolean => F[Unit]
  ): F[BackendConnection[F]] =  Async[F].async { cb =>
    val ws = new WebSocket(url.toString, Protocol)

    ws.onopen = { _: Event =>
      cb(Right(new WebSocketJSConnection(ws)))
    }

    ws.onmessage = { e: MessageEvent =>
      (e.data match {
        case str: String => onMessage(str)
        case other       =>
          Logger[F].error(s"Unexpected event from WebSocket for [$url]: [$other]")
      }).toIO.unsafeRunAsyncAndForget()
    }

    ws.onerror = { e: Event =>
      val exception = parse(js.JSON.stringify(e)).map(json => new GraphQLException(List(json)))
      onError(
        exception.getOrElse[Throwable](exception.swap.toOption.get)
      ).toIO.unsafeRunAsyncAndForget()
    }

    ws.onclose = { e: CloseEvent =>
      onClose(e.wasClean).toIO.unsafeRunAsyncAndForget()
    }
  }
}

object WebSocketJSBackend {
  def apply[F[_] : ConcurrentEffect : Logger]: WebSocketJSBackend[F] = new WebSocketJSBackend[F]
}
