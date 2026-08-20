// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.websocket

import clue.*
import io.circe.*

// Client internal state for the FSM.
// We keep a connectionId throughout all states to ensure that callback events (onClose, onMessage)
// correpond to the current connection iteration. This is important in case of reconnections.
protected enum State[F[_]](val status: PersistentClientStatus) {
  def connectionId: ConnectionId

  case Disconnected[F[_]](connectionId: ConnectionId, cause: Option[Throwable] = None)
      extends State[F](PersistentClientStatus.Disconnected)

  case Connecting[F[_]](
    connectionId:  ConnectionId,
    connection:    Option[WebSocketConnection[F]],
    initPayload:   F[JsonObject],
    subscriptions: Map[String, Emitter[F]],
    latch:         Latch[F, JsonObject]
  ) extends State[F](PersistentClientStatus.Connecting)

  case Connected[F[_]](
    connectionId:  ConnectionId,
    connection:    WebSocketConnection[F],
    initPayload:   F[JsonObject],
    subscriptions: Map[String, Emitter[F]]
  ) extends State[F](PersistentClientStatus.Connected)
}
