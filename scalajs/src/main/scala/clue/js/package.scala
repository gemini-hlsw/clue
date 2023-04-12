// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import clue.websocket.ApolloClient

package object js {
  type FetchJSClient[F[_], S] = FetchClientWithPars[F, FetchJSRequest, S]

  type WebSocketJSClient[F[_], S] = ApolloClient[F, String, S]
}
