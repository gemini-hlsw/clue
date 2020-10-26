// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.Eq
import cats.Show

sealed trait StreamingClientStatus
object StreamingClientStatus {
  final case object Connecting extends StreamingClientStatus
  final case object Open       extends StreamingClientStatus
  final case object Closing    extends StreamingClientStatus
  final case object Closed     extends StreamingClientStatus

  implicit val eqStreamingClientStatus: Eq[StreamingClientStatus]     = Eq.fromUniversalEquals
  implicit val showStreamingClientStatus: Show[StreamingClientStatus] = Show.fromToString
}
