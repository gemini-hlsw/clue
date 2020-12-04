// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.syntax.all._
import clue.model.StreamingMessage
import sttp.model.Uri

trait PersistentBackend[F[_]] {
  type CE // CloseEvent
  type CP // Close Parameters

  def connect(
    uri:       Uri,
    onMessage: String => F[Unit],
    onError:   Throwable => F[Unit],
    onClose:   CE => F[Unit] // Boolean = wasClean
  ): F[Connection]

  trait Connection {
    def send(msg:                    StreamingMessage.FromClient): F[Unit]
    final def close(closeParameters: CP): F[Unit] = closeInternal(closeParameters.some)
    final def close(): F[Unit] = closeInternal(none)
    protected[clue] def closeInternal(closeParameters: Option[CP]): F[Unit]
  }
}

object PersistentBackend {
  def apply[F[_]: PersistentBackend]: PersistentBackend[F] = implicitly
}
