// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.fsm

import cats.effect.ConcurrentEffect
import cats.effect.Timer
import io.chrisdavenport.log4cats.Logger
import sttp.model.Uri
import clue.WebSocketCloseParams
import clue.ReconnectionStrategy
import clue.WebSocketBackend
import clue.WebSocketCloseEvent

object ApolloWebSocketClient {
  def of[F[_]: ConcurrentEffect: Timer: Logger, S](
    uri:                  Uri,
    name:                 String = "",
    reconnectionStrategy: ReconnectionStrategy[WebSocketCloseEvent] = ReconnectionStrategy.never
  )(implicit backend:     WebSocketBackend[F]): F[ApolloWebSocketClient[F, S]] =
    ApolloClient[F, S, WebSocketCloseParams, WebSocketCloseEvent](uri, name, reconnectionStrategy)
}
