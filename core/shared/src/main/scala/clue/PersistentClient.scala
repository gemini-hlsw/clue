// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.syntax.all._
import cats.effect.Sync
import io.circe.Json

/**
 * A client that keeps a connection open with the server.
 */
trait PersistentClient[F[_], CP, CE] {
  protected val backend: PersistentBackend[F, CP, CE]
  protected val reconnectionStrategy: ReconnectionStrategy[CE]

  def status: F[StreamingClientStatus]

  def statusStream: fs2.Stream[F, StreamingClientStatus]

  def connect(payload: F[Map[String, Json]]): F[Unit]

  final def connect(payload: Map[String, Json] = Map.empty)(implicit sync: Sync[F]): F[Unit] =
    connect(Sync[F].delay(payload))

  final def disconnect(closeParameters: CP): F[Unit] =
    disconnectInternal(closeParameters.some)

  final def disconnect(): F[Unit] =
    disconnectInternal(none)

  protected def disconnectInternal(closeParameters: Option[CP]): F[Unit]
}
