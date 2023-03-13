// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

import cats.Eq
import cats.MonadError
import cats.effect.Concurrent
import cats.effect.Deferred
import cats.syntax.all._
import org.typelevel.log4cats.Logger

package object clue {
  protected[clue] type Latch[F[_]] = Deferred[F, Either[Throwable, Unit]]

  final implicit class StringOps(val str: String) extends AnyVal {
    def error[A]: Either[Throwable, A] =
      new Exception(str).asLeft[A]

    def raiseError[F[_], A](implicit F: MonadError[F, Throwable], logger: Logger[F]): F[A] =
      logger.error(str) >> F.raiseError(new Exception(str))

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
    def raiseF[F[_]](
      msg: String
    )(implicit F: MonadError[F, Throwable], logger: Logger[F]): F[Unit] =
      logger.error(t)(msg) >> F.raiseError(t)

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
      Deferred[F, Either[Throwable, Unit]]
  }

  protected[clue] class ConnectionId(val value: Int) extends AnyVal {
    def next: ConnectionId = new ConnectionId(value + 1)
  }

  protected[clue] object ConnectionId {
    val Zero: ConnectionId = new ConnectionId(0)

    implicit val eqConnectionId: Eq[ConnectionId] = Eq.by(_.value)
  }
}
