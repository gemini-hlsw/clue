// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

import cats.syntax.all._
import io.chrisdavenport.log4cats.Logger
import scala.concurrent.duration.FiniteDuration
import cats.MonadError
import cats.effect.Concurrent
import cats.effect.concurrent.Deferred

package object clue {
  type CloseReason[CE]               = Either[Throwable, CE]
  // Int = Attempt #. Will only be 0 immediately after a close.
  // For first connection, it will be called the first time with 1, after 1st connection attempt.
  type ReconnectionStrategy[CE]      = (Int, CloseReason[CE]) => Option[FiniteDuration]
  type WebSocketReconnectionStrategy = ReconnectionStrategy[WebSocketCloseEvent]

  type GraphQLWebSocketClient[F[_], S] =
    PersistentStreamingClient[F, S, WebSocketCloseParams, WebSocketCloseEvent]

  type WebSocketBackend[F[_]]    = PersistentBackend[F, WebSocketCloseParams, WebSocketCloseEvent]
  type WebSocketConnection[F[_]] = PersistentConnection[F, WebSocketCloseParams]

  type ApolloWebSocketClient[F[_], S] =
    ApolloClient[F, S, WebSocketCloseParams, WebSocketCloseEvent]

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
      msg:        String
    )(implicit F: MonadError[F, Throwable], logger: Logger[F]): F[Unit] =
      logger.error(t)(msg) >> F.raiseError(t)

    def logF[F[_]](
      msg:             String
    )(implicit logger: Logger[F]): F[Unit] =
      logger.error(t)(msg)

    def warnF[F[_]](msg: String)(implicit logger: Logger[F]): F[Unit] =
      logger.warn(t)(msg)
  }
}

package clue {
  object ReconnectionStrategy {
    def never[CE]: ReconnectionStrategy[CE] = (_, _) => none
  }

  object WebSocketReconnectionStrategy {
    def never: WebSocketReconnectionStrategy = ReconnectionStrategy.never
  }

  protected[clue] object Latch {
    def apply[F[_]: Concurrent]: F[Latch[F]] =
      Deferred[F, Either[Throwable, Unit]]
  }
}
