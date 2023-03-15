// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.syntax.all._

import scala.concurrent.duration.FiniteDuration

package object websocket {
  // A Left(exception) here indicates an unexpected/errored disconnection from the server.
  type CloseEvent = Either[Throwable, CloseParams]

  // A Left(exception) here indicates an error establishing the connection.
  type ReconnectReason = Either[Throwable, CloseEvent]

  // Int = Attempt #. Will only be 0 immediately after a close (whether graceful or errored).
  // If the very fist connection attempt fails, the first invocation will have Attempt = 1.
  type ReconnectionStrategy = (Int, ReconnectReason) => Option[FiniteDuration]

  type WebSocketBackend[F[_], P] = PersistentBackend[F, P, CloseParams, CloseEvent]

  type WebSocketConnection[F[_]] = PersistentConnection[F, CloseParams]

  type WebSocketClient[F[_], S] = PersistentStreamingClient[F, S, CloseParams, CloseEvent]

  type WebSocketHandler[F[_]] = PersistentBackendHandler[F, CloseEvent]
}

package websocket {
  object ReconnectionStrategy {
    def never[CE]: ReconnectionStrategy = (_, _) => none
  }

}
