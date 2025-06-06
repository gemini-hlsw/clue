// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.js

import cats.effect.Async
import cats.effect.std.SecureRandom
import clue.websocket.*
import org.typelevel.log4cats.Logger

object WebSocketJsClient {
  def of[F[_]: Async: WebSocketJsBackend: Logger: SecureRandom, S](
    uri:                  String,
    name:                 String = "",
    reconnectionStrategy: ReconnectionStrategy = ReconnectionStrategy.never
  ): F[WebSocketJsClient[F, S]] =
    ApolloClient.of[F, String, S](uri, name, reconnectionStrategy)
}
