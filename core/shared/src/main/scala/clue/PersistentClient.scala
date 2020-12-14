// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.syntax.all._
import cats.effect.Sync
import io.circe.Json

sealed trait TerminateOptions[+CP]
object TerminateOptions {
  final case object KeepConnection extends TerminateOptions[Nothing]
  final case class Disconnect[CP](closeParameters: Option[CP] = none) extends TerminateOptions[CP]
  object Disconnect {
    def apply[CP](closeParameters: CP): Disconnect[CP] = Disconnect(closeParameters.some)
  }
}

/**
 * A client that keeps a connection open with the server.
 */
trait PersistentClient[F[_], CP, CE] {
  protected val backend: PersistentBackend[F, CP, CE]
  protected val reconnectionStrategy: ReconnectionStrategy[CE]

  def status: F[StreamingClientStatus]

  def statusStream: fs2.Stream[F, StreamingClientStatus]

  def init(payload: F[Map[String, Json]]): F[Unit]

  final def init(payload: Map[String, Json] = Map.empty)(implicit sync: Sync[F]): F[Unit] =
    init(Sync[F].delay(payload))

  final def terminate(
    terminateOptions:  TerminateOptions[CP],
    keepSubscriptions: Boolean = false
  ): F[Unit] =
    terminateInternal(terminateOptions, keepSubscriptions)

  protected def terminateInternal(
    terminateOptions:  TerminateOptions[CP],
    keepSubscriptions: Boolean
  ): F[Unit]
}
