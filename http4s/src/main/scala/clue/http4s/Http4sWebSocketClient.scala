// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.http4s

import cats.effect.Async
import clue.websocket.ApolloWebSocketClient
import clue.websocket.WebSocketReconnectionStrategy
import org.http4s.Uri
import org.typelevel.log4cats.Logger

object Http4sWebSocketClient {
  def of[F[_]: Async: Logger, S](
    uri:                  Uri,
    name:                 String = "",
    reconnectionStrategy: WebSocketReconnectionStrategy = WebSocketReconnectionStrategy.never
  )(implicit backend: Http4sWebSocketBackend[F]): F[ApolloWebSocketClient[F, Uri, S]] =
    ApolloWebSocketClient.of[F, Uri, S](uri, name, reconnectionStrategy)
}
