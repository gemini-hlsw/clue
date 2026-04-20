// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import clue.websocket.ApolloClient
import org.scalajs.dom.Headers

package object js {
  type FetchJsClient[F[_], S] = FetchClientWithPars[F, FetchJsRequest, S]

  type WebSocketJsClient[F[_], S] = ApolloClient[F, String, S]

  given TraceHeaderInjector[FetchJsRequest] with
    def addHeaders(params: FetchJsRequest, headers: Map[String, String]): FetchJsRequest =
      val newHeaders = new Headers(params.headers)
      headers.foreach((k, v) => newHeaders.set(k, v))
      params.copy(headers = newHeaders)
}
