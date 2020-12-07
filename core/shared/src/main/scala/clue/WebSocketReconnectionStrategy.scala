// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import scala.concurrent.duration.FiniteDuration

object WebSocketReconnectionStrategy {
  def apply[F[_]](
    maxAttempts: Int,
    backoffFn:   (Int, WebSocketCloseEvent) => Option[FiniteDuration]
  ): WebSocketReconnectionStrategy[F] = ReconnectionStrategy(maxAttempts, backoffFn)
}
