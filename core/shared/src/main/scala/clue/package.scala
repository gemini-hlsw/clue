// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

import cats.syntax.all._
import scala.concurrent.duration.FiniteDuration

package object clue {
  type ReconnectionStrategy[CE]      = (Int, CE) => Option[FiniteDuration]
  type WebSocketReconnectionStrategy = ReconnectionStrategy[WebSocketCloseEvent]
}

package clue {
  object ReconnectionStrategy {
    def never[CE]: ReconnectionStrategy[CE] = (_, _) => none
  }

  object WebSocketReconnectionStrategy {
    def never: WebSocketReconnectionStrategy = ReconnectionStrategy.never
  }
}
