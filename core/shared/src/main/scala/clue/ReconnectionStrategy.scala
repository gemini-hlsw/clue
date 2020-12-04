// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.effect.Timer
import cats.effect.Sync
import scala.concurrent.duration.FiniteDuration

final case class ReconnectionStrategy[F[_]: Timer: Sync, CE](
  maxAttempts: Int,
  backoffFn:   (Int, CE) => Option[FiniteDuration] // If None is returned, no more reconnect attempts
)
