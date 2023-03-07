// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

package object websocket {
  type WebSocketReconnectionStrategy = ReconnectionStrategy[WebSocketCloseEvent]

  object WebSocketReconnectionStrategy {
    def never: WebSocketReconnectionStrategy = ReconnectionStrategy.never
  }

  type WebSocketBackend[F[_], P] =
    PersistentBackend[F, P, WebSocketCloseParams, WebSocketCloseEvent]

  type WebSocketConnection[F[_]] = PersistentConnection[F, WebSocketCloseParams]

  type ApolloWebSocketClient[F[_], P, S] =
    ApolloClient[F, P, S, WebSocketCloseParams, WebSocketCloseEvent]

  type WebSocketCloseEvent = Either[Throwable, WebSocketCloseParams]

  type WebSocketClient[F[_], S] =
    PersistentStreamingClient[F, S, WebSocketCloseParams, WebSocketCloseEvent]

  type WebSocketHandler[F[_]] = PersistentBackendHandler[F, WebSocketCloseEvent]
}
