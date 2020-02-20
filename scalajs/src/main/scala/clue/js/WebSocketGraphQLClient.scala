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
case class WebSocketGraphQLClient[F[_]](url: Url)(implicit
  protected val ceF: ConcurrentEffect[F],
  protected val tF: Timer[F]
) extends ApolloStreamingClient[F] {

  private val Protocol = "graphql-ws"

  type WebSocketClient = WebSocket

  private case class WebSocketSender(private val ws: WebSocketClient) extends Sender {
    def send(msg: StreamingMessage): F[Unit] =
      Sync[F].delay(ws.send(msg.asJson.toString))

    protected[clue] def close(): F[Unit] =
      Sync[F].delay(ws.close())
  }

  protected def createClientInternal(
    onOpen:    Sender => F[Unit],
    onMessage: String => F[Unit],
    onError:   Throwable => F[Unit],
    onClose:   Boolean => F[Unit]
  ): F[Unit] = Sync[F].delay {
    val ws = new WebSocket(url.toString, Protocol)

    ws.onopen = { _: Event =>
      onOpen(WebSocketSender(ws)).toIO.unsafeRunAsyncAndForget()
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
