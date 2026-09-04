// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.js

import cats.effect.*
import cats.effect.std.Dispatcher
import cats.effect.std.Queue
import cats.effect.syntax.all.*
import cats.syntax.all.*
import clue.*
import clue.model.StreamingMessage
import clue.model.json.given
import clue.websocket.*
import fs2.Stream
import io.circe.syntax.*
import org.scalajs.dom.Event
import org.scalajs.dom.MessageEvent
import org.scalajs.dom.WebSocket
import org.typelevel.log4cats.Logger

/**
 * Events received from the JS WebSocket, before they are interpreted in `F`.
 */
private[js] enum WebSocketEvent {
  case Message(data: String)
  case UnexpectedData(data: Any)
  case Closed(event: CloseEvent)

  /** `Closed` is always the last event on a socket. */
  def isTerminal: Boolean = this match {
    case Closed(_) => true
    case _         => false
  }
}

/**
 * Streaming backend for Js WebSocket.
 */
final class WebSocketJsBackend[F[_]: Async: Logger](dispatcher: Dispatcher[F])
    extends WebSocketBackend[F, String] {
  private val Protocol = "graphql-transport-ws"

  override def connect(
    uri:          String,
    handler:      WebSocketHandler[F],
    connectionId: ConnectionId
  ): F[WebSocketConnection[F]] =
    // Only `open` is cancelable. It closes the socket if the client cancels it.
    Async[F].uncancelable { poll =>
      for {
        queue <- Queue.unbounded[F, WebSocketEvent]
        ws    <- poll(open(uri, queue))
        _     <- s"WebSocket open for URI [$uri]".traceF
        _     <- listen(uri, queue, handler, connectionId).start
      } yield new WebSocketJsConnection(ws)
    }

  private def open(uri: String, queue: Queue[F, WebSocketEvent]): F[WebSocket] = {
    def enqueue(event: WebSocketEvent): Unit =
      dispatcher.unsafeRunAndForget(queue.offer(event))

    Async[F].async[WebSocket] { cb =>
      Sync[F].delay {
        val ws = new WebSocket(uri, Protocol)

        // `onclose` reads this to decide how the close is reported.
        var errored = false

        ws.onopen = { (_: Event) =>
          cb(ws.asRight)
        }

        // Per spec, onError fires only before close, never after.
        // https://html.spec.whatwg.org/multipage/web-sockets.html
        ws.onerror = { (_: Event) =>
          errored = true
          // If socket already opened, this callback is dropped. `async` honors only the first.
          cb(ConnectionException("Could not establish connection").asLeft)
        }

        ws.onmessage = { (e: MessageEvent) =>
          e.data match {
            case str: String => enqueue(WebSocketEvent.Message(str))
            case other       => enqueue(WebSocketEvent.UnexpectedData(other))
          }
        }

        ws.onclose = { (e: org.scalajs.dom.CloseEvent) =>
          val closeParams: CloseParams = CloseParams(e.code, e.reason)
          enqueue(
            WebSocketEvent.Closed(
              if (errored) DisconnectedException(closeParams.show).asLeft
              else closeParams.asRight
            )
          )
        }

        Sync[F].delay(ws.close(1000, "Web Socket initialization canceled by client")).some
      }
    }
  }

  private def listen(
    uri:          String,
    queue:        Queue[F, WebSocketEvent],
    handler:      WebSocketHandler[F],
    connectionId: ConnectionId
  ): F[Unit] =
    def handle(event: WebSocketEvent): F[Unit] = event match {
      case WebSocketEvent.Message(data)        => handler.onMessage(connectionId, data)
      case WebSocketEvent.UnexpectedData(data) =>
        s"Unexpected event from WebSocket for [$uri]: [$data]".errorF
      case WebSocketEvent.Closed(event)        =>
        event.swap.traverse_(t => s"Error on WebSocket for [$uri]: [${t.getMessage}]".errorF) >>
          s"WebSocket closed for URI [$uri]".traceF >>
          handler.onClose(connectionId, event)
    }

    Stream
      .fromQueueUnterminated(queue)
      // One failed event must not end the listener. Otherwise the terminal `Closed` event is
      // never delivered, the client never reconnects, and the queue grows without a consumer.
      .evalTap: event =>
        handle(event).handleErrorWith(_.logF(s"Error handling WebSocket event for [$uri]"))
      .takeThrough(!_.isTerminal)
      .compile
      .drain
      .handleErrorWith(_.logF(s"Error in WebSocket listener for [$uri]"))
}

object WebSocketJsBackend {
  def apply[F[_]: Async: Logger](dispatcher: Dispatcher[F]): WebSocketJsBackend[F] =
    new WebSocketJsBackend[F](dispatcher)
}

final class WebSocketJsConnection[F[_]: Sync: Logger](private val ws: WebSocket)
    extends WebSocketConnection[F] {
  override def send(msg: StreamingMessage.FromClient): F[Unit] =
    Sync[F].delay(ws.send(msg.asJson.noSpaces))

  override def closeInternal(closeParameters: Option[CloseParams]): F[Unit] =
    "Disconnecting WebSocket...".traceF >>
      Sync[F].delay {
        val params = closeParameters.getOrElse(CloseParams())
        // ws.close facade doesn't use js.Undef for optional params, so we handle all cases.
        (params.code, params.reason)
          .mapN((code, reason) => ws.close(code, reason))
          .orElse(params.code.map(code => ws.close(code)))
          .orElse(params.reason.map(reason => ws.close(reason = reason)))
          .getOrElse(ws.close())
      }
}
