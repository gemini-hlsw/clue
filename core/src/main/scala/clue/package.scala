// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

import cats.Eq
import cats.Functor
import cats.MonadError
import cats.effect.Concurrent
import cats.effect.Deferred
import cats.effect.MonadCancelThrow
import cats.syntax.all.*
import org.typelevel.log4cats.Logger

package object clue {
  type FetchClient[F[_], S] = FetchClientWithPars[F, ?, S]

  // The Latch value is interpreted as:
  //   None = canceled, Some(Right(())) = done, Some(Left(t)) = error
  protected[clue] type Latch[F[_]] = Deferred[F, Option[Either[Throwable, Unit]]]

  extension [F[_]](latch: Latch[F]) {
    def resolve(using F: MonadCancelThrow[F]): F[Unit] =
      latch.get.flatMap(_.fold(F.canceled)(_.fold(F.raiseError, F.pure)))

    def release(using Functor[F]): F[Unit] =
      latch.complete(().asRight.some).void

    def error(t: Throwable)(using Functor[F]): F[Unit] =
      latch.complete(t.asLeft.some).void

    def cancel(using Functor[F]): F[Unit] =
      latch.complete(none).void
  }

  extension (str: String) {
    def errorF[F[_]](using logger: Logger[F]): F[Unit] =
      logger.error(str)

    def warnF[F[_]](using logger: Logger[F]): F[Unit] =
      logger.warn(str)

    def debugF[F[_]](using logger: Logger[F]): F[Unit] =
      logger.debug(str)

    def traceF[F[_]](using logger: Logger[F]): F[Unit] =
      logger.trace(str)
  }

  extension (t: Throwable) {
    def logAndRaiseF[F[_]](using MonadError[F, Throwable], Logger[F]): F[Unit] =
      logAndRaiseF_[F, Unit]

    def logAndRaiseF_[F[_], A](using F: MonadError[F, Throwable])(using logger: Logger[F]): F[A] =
      logger.error(t)("") >> F.raiseError[A](t)

    def raiseF[F[_]](using F: MonadError[F, Throwable]): F[Unit] =
      F.raiseError(t)

    def logF[F[_]](msg: String)(using logger: Logger[F]): F[Unit] =
      logger.error(t)(msg)

    def warnF[F[_]](msg: String)(using logger: Logger[F]): F[Unit] =
      logger.warn(t)(msg)

    def debugF[F[_]](msg: String)(using logger: Logger[F]): F[Unit] =
      logger.debug(t)(msg)
  }
}

package clue {
  protected[clue] object Latch {
    def apply[F[_]: Concurrent]: F[Latch[F]] =
      Deferred[F, Option[Either[Throwable, Unit]]]
  }

  protected[clue] class ConnectionId(val value: Int) extends AnyVal {
    def next: ConnectionId = new ConnectionId(value + 1)
  }

  protected[clue] object ConnectionId {
    val Zero: ConnectionId = new ConnectionId(0)

    given Eq[ConnectionId] = Eq.by(_.value)
  }
}
