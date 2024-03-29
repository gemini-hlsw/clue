// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.js

import cats.effect.Async
import clue.websocket.*
import org.typelevel.log4cats.Logger

object WebSocketJSClient {
  def of[F[_]: Async: Logger, S](
    uri:                  String,
    name:                 String = "",
    reconnectionStrategy: ReconnectionStrategy = ReconnectionStrategy.never
  )(implicit backend: WebSocketJSBackend[F]): F[WebSocketJSClient[F, S]] =
    ApolloClient.of[F, String, S](uri, name, reconnectionStrategy)
}
