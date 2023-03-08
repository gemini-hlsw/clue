// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import clue.websocket.ApolloWebSocketClient

package object js {
  type FetchJSClient[F[_], S] = FetchClient[F, FetchJSRequest, S]

  type WebSocketJSClient[F[_], S] = ApolloWebSocketClient[F, String, S]
}
