// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.js

import cats.effect.*
import cats.syntax.all.*
import clue.*
import clue.model.GraphQLRequest
import clue.model.json.given
import io.circe.Encoder
import io.circe.syntax.*
import org.scalajs.dom.AbortController
import org.scalajs.dom.Fetch
import org.scalajs.dom.Headers
import org.scalajs.dom.HttpMethod
import org.scalajs.dom.RequestInit
import org.scalajs.dom.Response

final class FetchJsBackend[F[_]: Async] extends FetchBackend[F, FetchJsRequest] {
  override def request[V: Encoder](
    request:     GraphQLRequest[V],
    baseRequest: FetchJsRequest
  ): F[String] =
    Sync[F].defer {
      val controller = new AbortController()
      val abort      = Sync[F].delay(controller.abort())

      val fetch: F[Response] =
        Async[F].fromPromiseCancelable(
          Sync[F].delay {
            val _signal  = controller.signal
            val _headers = new Headers(baseRequest.headers)
            // The specification requires the GraphQL media type in the `Accept` header. A header
            // that the caller set stays unchanged.
            if (!_headers.has(FetchJsBackend.AcceptHeaderName))
              _headers.set(FetchJsBackend.AcceptHeaderName, FetchJsBackend.AcceptHeaderValue)
            _headers.set("Content-Type", "application/json")
            val promise  = Fetch
              .fetch(
                baseRequest.uri.toString,
                new RequestInit {
                  method = HttpMethod.POST
                  body = request.asJson.noSpaces
                  headers = _headers
                  signal = _signal
                }
              )
            (promise, abort)
          }
        )

      fetch.flatMap { response =>
        Async[F]
          .fromPromiseCancelable(Sync[F].delay((response.text(), abort)))
          .flatMap { body =>
            val contentType = Option(response.headers.get("Content-Type"))
            if (GraphQLOverHttp.processBody(response.status, contentType))
              body.pure[F]
            else
              HttpStatusException(response.status, contentType, body).raiseError[F, String]
          }
      }
    }
}

object FetchJsBackend {
  def apply[F[_]: Async](): FetchJsBackend[F] =
    new FetchJsBackend[F]

  /** The name of the `Accept` header. */
  private val AcceptHeaderName: String = "Accept"

  /** The legacy media type for GraphQL responses. */
  private val JsonMediaType: String = "application/json"

  /**
   * The value of the `Accept` header that the specification recommends. It asks for the GraphQL
   * media type and accepts the legacy JSON media type for older servers.
   */
  private val AcceptHeaderValue: String =
    s"${GraphQLOverHttp.GraphQLResponseMediaType}, $JsonMediaType;q=0.9"
}
