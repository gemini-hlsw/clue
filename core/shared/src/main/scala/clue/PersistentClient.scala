// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.syntax.all._
import io.circe.JsonObject
import cats.effect.Sync

/**
 * A client that keeps a connection open with the server.
 */
trait PersistentClient[F[_]] {
  protected implicit val backend: PersistentBackend[F]

  type CP = backend.CP
  type CE = backend.CE

  def status: F[StreamingClientStatus]

  def statusStream: fs2.Stream[F, StreamingClientStatus]

  def connect(
    payload:              F[JsonObject],
    reconnectionStrategy: Option[ReconnectionStrategy[F, backend.CE]]
  ): F[Unit]

  def connect(
    payload:              JsonObject = JsonObject.empty,
    reconnectionStrategy: Option[ReconnectionStrategy[F, backend.CE]] = None
  )(implicit sync:        Sync[F]): F[Unit] =
    connect(Sync[F].delay(payload), reconnectionStrategy)

  final def disconnect(closeParameters: backend.CP): F[Unit] =
    disconnectInternal(closeParameters.some)

  final def disconnect(): F[Unit] =
    disconnectInternal(none)

  protected def disconnectInternal(closeParameters: Option[backend.CP]): F[Unit]
}
