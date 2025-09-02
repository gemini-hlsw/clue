// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.websocket

import clue.*
import io.circe.*

// Client internal state for the FSM.
// We keep a connectionId throughout all states to ensure that callback events (onClose, onMessage)
// correpond to the current connection iteration. This is important in case of reconnections.
protected sealed abstract class State[F[_]](val status: PersistentClientStatus) {
  val connectionId: ConnectionId
}

protected object State {
  final case class Disconnected[F[_]](connectionId: ConnectionId)
      extends State[F](PersistentClientStatus.Disconnected)

  final case class Connecting[F[_]](
    connectionId:  ConnectionId,
    connection:    Option[WebSocketConnection[F]],
    initPayload:   F[JsonObject],
    subscriptions: Map[String, Emitter[F]],
    latch:         Latch[F, JsonObject]
  ) extends State[F](PersistentClientStatus.Connecting)

  final case class Connected[F[_]](
    connectionId:  ConnectionId,
    connection:    WebSocketConnection[F],
    initPayload:   F[JsonObject],
    subscriptions: Map[String, Emitter[F]]
  ) extends State[F](PersistentClientStatus.Connected)
}
