// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
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

  final implicit class LatchOps[F[_]](val latch: Latch[F]) extends AnyVal {
    def resolve(implicit F: MonadCancelThrow[F]): F[Unit] =
      latch.get.flatMap(_.fold(F.canceled)(_.fold(F.raiseError, F.pure)))

    def release(implicit F: Functor[F]): F[Unit] =
      latch.complete(().asRight.some).void

    def error(t: Throwable)(implicit F: Functor[F]): F[Unit] =
      latch.complete(t.asLeft.some).void

    def cancel(implicit F: Functor[F]): F[Unit] =
      latch.complete(none).void
  }

  final implicit class StringOps(val str: String) extends AnyVal {
    def errorF[F[_]](implicit logger: Logger[F]): F[Unit] =
      logger.error(str)

    def warnF[F[_]](implicit logger: Logger[F]): F[Unit] =
      logger.warn(str)

    def debugF[F[_]](implicit logger: Logger[F]): F[Unit] =
      logger.debug(str)

    def traceF[F[_]](implicit logger: Logger[F]): F[Unit] =
      logger.trace(str)
  }

  final implicit class ThrowableOps(val t: Throwable) extends AnyVal {
    def logAndRaiseF[F[_]](implicit F: MonadError[F, Throwable], logger: Logger[F]): F[Unit] =
      logAndRaiseF_[F, Unit]

    def logAndRaiseF_[F[_], A](implicit F: MonadError[F, Throwable], logger: Logger[F]): F[A] =
      logger.error(t)("") >> F.raiseError[A](t)

    def logF[F[_]](
      msg: String
    )(implicit logger: Logger[F]): F[Unit] =
      logger.error(t)(msg)

    def warnF[F[_]](msg: String)(implicit logger: Logger[F]): F[Unit] =
      logger.warn(t)(msg)

    def debugF[F[_]](msg: String)(implicit logger: Logger[F]): F[Unit] =
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

    implicit val eqConnectionId: Eq[ConnectionId] = Eq.by(_.value)
  }
}
