// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.syntax.all._
import clue.model.StreamingMessage
import sttp.model.Uri

// CP = Close Parameters, sent by client when close is requested.
// CE = Close Event, received by client when server closes.
trait PersistentBackend[F[_], CP, CE] {
  def connect(
    uri:       Uri,
    onMessage: String => F[Unit],
    onError:   Throwable => F[Unit],
    onClose:   CE => F[Unit]
  ): F[PersistentConnection[F, CP]]
}

object PersistentBackend {
  def apply[F[_], CP, CE](implicit
    backend: PersistentBackend[F, CP, CE]
  ): PersistentBackend[F, CP, CE] = backend
}

trait PersistentConnection[F[_], CP] {
  def send(msg:                    StreamingMessage.FromClient): F[Unit]
  final def close(closeParameters: CP): F[Unit] = closeInternal(closeParameters.some)
  final def close(): F[Unit] = closeInternal(none)
  protected[clue] def closeInternal(closeParameters: Option[CP]): F[Unit]
}
