// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.websocket

import cats.effect.Async
import clue.ApolloClient
import org.typelevel.log4cats.Logger

object ApolloWebSocketClient {
  def of[F[_]: Async: Logger, P, S](
    connectionParams:     P,
    name:                 String = "",
    reconnectionStrategy: WebSocketReconnectionStrategy = WebSocketReconnectionStrategy.never
  )(implicit backend: WebSocketBackend[F, P]): F[ApolloWebSocketClient[F, P, S]] =
    ApolloClient[F, P, S, WebSocketCloseParams, WebSocketCloseEvent](
      connectionParams,
      name,
      reconnectionStrategy
    )
}
