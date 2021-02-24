// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.Eq
import cats.Show

sealed trait PersistentClientStatus
object PersistentClientStatus {
  final case object Connecting   extends PersistentClientStatus
  final case object Connected    extends PersistentClientStatus
  final case object Initializing extends PersistentClientStatus
  final case object Initialized  extends PersistentClientStatus
  final case object Disconnected extends PersistentClientStatus

  implicit val eqStreamingClientStatus: Eq[PersistentClientStatus]     = Eq.fromUniversalEquals
  implicit val showStreamingClientStatus: Show[PersistentClientStatus] = Show.fromToString
}
