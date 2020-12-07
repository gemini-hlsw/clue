// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.syntax.all._
import cats.effect.ConcurrentEffect
import cats.effect.Timer
import io.chrisdavenport.log4cats.Logger
import sttp.model.Uri
import fs2.concurrent.SignallingRef
import cats.effect.concurrent.Ref
import cats.effect.concurrent.MVar
import cats.effect.concurrent.MVar2
import scala.concurrent.duration.FiniteDuration

case class ApolloWebSocketClient[F[_]: ConcurrentEffect: Timer: Logger, S](
  uri:                                         Uri,
  override protected val backend:              WebSocketBackend[F],
  override val connectionStatus:               SignallingRef[F, StreamingClientStatus],
  override protected val subscriptions:        Ref[F, Map[String, Emitter[F]]],
  override protected val connectionMVar:       MVar2[
    F,
    Either[Throwable, PersistentConnection[F, WebSocketCloseParams]]
  ],
  override protected val connectionAttempt:    Ref[F, Int],
  override protected val reconnectionStrategy: Option[ReconnectionStrategy[F, WebSocketCloseEvent]]
) extends ApolloClient[F, S, WebSocketCloseParams, WebSocketCloseEvent](uri)
    with GraphQLWebSocketClient[F, S] {
  def withReconnectionStrategy(
    reconnectionStrategy: ReconnectionStrategy[F, WebSocketCloseEvent]
  ): ApolloWebSocketClient[F, S] = copy(reconnectionStrategy = reconnectionStrategy.some)
}

object ApolloWebSocketClient {
  def of[F[_]: ConcurrentEffect: Timer: Logger, S](
    uri:                  Uri,
    reconnectionStrategy: Option[ReconnectionStrategy[F, WebSocketCloseEvent]] = none
  )(implicit backend:     WebSocketBackend[F]): F[ApolloWebSocketClient[F, S]] =
    for {
      connectionStatus  <-
        SignallingRef[F, StreamingClientStatus](StreamingClientStatus.Disconnected)
      subscriptions     <- Ref.of[F, Map[String, Emitter[F]]](Map.empty)
      connectionMVar    <-
        MVar.empty[F, Either[Throwable, PersistentConnection[F, WebSocketCloseParams]]]
      connectionAttempt <- Ref.of[F, Int](0)
    } yield new ApolloWebSocketClient[F, S](uri,
                                            backend,
                                            connectionStatus,
                                            subscriptions,
                                            connectionMVar,
                                            connectionAttempt,
                                            reconnectionStrategy
    )

  def of[F[_]: ConcurrentEffect: Timer: Logger, S](
    uri:                  Uri,
    reconnectionStrategy: ReconnectionStrategy[F, WebSocketCloseEvent]
  )(implicit backend:     WebSocketBackend[F]): F[ApolloWebSocketClient[F, S]] =
    of(uri, reconnectionStrategy.some)

  def of[F[_]: ConcurrentEffect: Timer: Logger, S](
    uri:              Uri,
    maxAttempts:      Int,
    backoffFn:        (Int, WebSocketCloseEvent) => Option[FiniteDuration]
  )(implicit backend: WebSocketBackend[F]): F[ApolloWebSocketClient[F, S]] =
    of(uri, ReconnectionStrategy[F, WebSocketCloseEvent](maxAttempts, backoffFn).some)
}
