// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import io.circe.JsonObject

/**
 * A client that keeps a connection open with the server.
 */
trait PersistentClient[F[_]] {
  def status: F[StreamingClientStatus]

  def statusStream: fs2.Stream[F, StreamingClientStatus]

  def connect(payload: JsonObject = JsonObject.empty): F[Unit]

  def disconnect(): F[Unit]
}
