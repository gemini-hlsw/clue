// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.Eq
import cats.Show

sealed trait PersistentClientStatus
object PersistentClientStatus {
  case object Connecting   extends PersistentClientStatus
  case object Connected    extends PersistentClientStatus
  case object Initializing extends PersistentClientStatus
  case object Initialized  extends PersistentClientStatus
  case object Disconnected extends PersistentClientStatus

  implicit val eqStreamingClientStatus: Eq[PersistentClientStatus]     = Eq.fromUniversalEquals
  implicit val showStreamingClientStatus: Show[PersistentClientStatus] = Show.fromToString
}
