// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

import cats.syntax.all._
import io.chrisdavenport.log4cats.Logger
import scala.concurrent.duration.FiniteDuration
import cats.MonadError

package object clue {
  type CloseReason[CE]               = Either[Throwable, CE]
  type ReconnectionStrategy[CE]      = (Int, CloseReason[CE]) => Option[FiniteDuration]
  type WebSocketReconnectionStrategy = ReconnectionStrategy[WebSocketCloseEvent]

  final implicit class StringOps(val str: String) extends AnyVal {
    private def prefixedMsg(implicit prefix: LogPrefix): String = s"[${prefix.value}] $str"

    def error[A](implicit prefix: LogPrefix): Either[Throwable, A] =
      new Exception(prefixedMsg).asLeft[A]

    def raiseError[F[_]](implicit
      F:      MonadError[F, Throwable],
      logger: Logger[F],
      prefix: LogPrefix
    ): F[Unit] = {
      val msg = prefixedMsg
      logger.error(msg) >> F.raiseError(new Exception(msg))
    }

    def warnF[F[_]](implicit
      logger: Logger[F],
      prefix: LogPrefix
    ): F[Unit] =
      logger.warn(prefixedMsg)
  }

  final implicit class ThrowableOps(val t: Throwable) extends AnyVal {
    private def prefixedMsg(msg: String)(implicit prefix: LogPrefix): String =
      s"[${prefix.value}] $msg"

    def raiseF[F[_]](msg: String)(implicit
      F:                  MonadError[F, Throwable],
      logger:             Logger[F],
      prefix:             LogPrefix
    ): F[Unit] =
      logger.error(t)(prefixedMsg(msg)) >> F.raiseError(t)

    def warnF[F[_]](msg: String)(implicit
      logger:            Logger[F],
      prefix:            LogPrefix
    ): F[Unit] =
      logger.warn(t)(prefixedMsg(msg))

  }

  // final implicit class EffectOps[F[_], A](val f: F[A]) extends AnyVal {
  //   def debug(implicit F: FlatMap[F], logger: Logger[F], prefix: LogPrefix): F[A] =
  //     // TODO Log errors? Or use another method?
  //     f.flatTap(v => logger.debug(s"[${prefix.value}] $v"))

  //   def error[B](implicit
  //     F:      MonadError[F, Throwable],
  //     logger: Logger[F],
  //     prefix: LogPrefix
  //   ): F[B] =
  //     f.map(v => s"[${prefix.value}] $v")
  //       .flatTap(msg => logger.error(msg))
  //       .map(msg => new Exception(msg).asLeft[B])
  //       .rethrow

  //   def warn(implicit F: FlatMap[F], logger: Logger[F], prefix: LogPrefix): F[Unit] =
  //     // TODO Log errors? Or use another method?
  //     f.flatMap(v => logger.warn(s"[${prefix.value}] $v"))

  //   // def log(implicit F: FlatMap[F], logger: Logger[F], prefix: LogPrefix): F[Unit] =
  //   //   // TODO Log errors? Or use another method?
  //   //   f.flatMap(v => logger.info(s"[${prefix.value}] $v"))

  // }
}

package clue {
  final class LogPrefix(val value: String) extends AnyVal
  object ReconnectionStrategy {
    def never[CE]: ReconnectionStrategy[CE] = (_, _) => none
  }

  object WebSocketReconnectionStrategy {
    def never: WebSocketReconnectionStrategy = ReconnectionStrategy.never
  }

}
