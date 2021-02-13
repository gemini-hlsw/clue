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
import cats.effect.concurrent.Deferred

case class ApolloWebSocketClient[F[_]: ConcurrentEffect: Timer: Logger, S](
  uri:                                         Uri,
  name:                                        String,
  override protected val backend:              WebSocketBackend[F],
  override val connectionStatus:               SignallingRef[F, StreamingClientStatus],
  override protected val subscriptions:        Ref[F, Map[String, Emitter[F]]],
  override protected val firstInitInvoked:     Deferred[F, Unit],
  override protected val connectionRef:        Ref[F, ApolloClient.Connection[F, WebSocketCloseParams]],
  override protected val connectionAttempt:    Ref[F, Int],
  override protected val reconnectionStrategy: ReconnectionStrategy[WebSocketCloseEvent]
) extends ApolloClient[F, S, WebSocketCloseParams, WebSocketCloseEvent](uri, name)
    with GraphQLWebSocketClient[F, S]

object ApolloWebSocketClient {
  def of[F[_]: ConcurrentEffect: Timer: Logger, S](
    uri:                  Uri,
    name:                 String = "",
    reconnectionStrategy: ReconnectionStrategy[WebSocketCloseEvent] = ReconnectionStrategy.never
  )(implicit backend:     WebSocketBackend[F]): F[ApolloWebSocketClient[F, S]] =
    for {
      connectionStatus  <-
        SignallingRef[F, StreamingClientStatus](StreamingClientStatus.Disconnected)
      subscriptions     <- Ref.of[F, Map[String, Emitter[F]]](Map.empty)
      firstInitInvoked  <- Deferred[F, Unit]
      connectionRef     <-
        Ref.of[F, ApolloClient.Connection[F, WebSocketCloseParams]](none)
      connectionAttempt <- Ref.of[F, Int](0)
    } yield new ApolloWebSocketClient[F, S](uri,
                                            name,
                                            backend,
                                            connectionStatus,
                                            subscriptions,
                                            firstInitInvoked,
                                            connectionRef,
                                            connectionAttempt,
                                            reconnectionStrategy
    )
}
