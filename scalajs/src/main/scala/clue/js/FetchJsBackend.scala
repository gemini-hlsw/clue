// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.js

import cats.effect.*
import cats.syntax.all.*
import clue.*
import clue.model.GraphQLQuery
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

import scala.scalajs.js.URIUtils

sealed trait FetchMethod extends Product with Serializable
object FetchMethod {
  case object GET  extends FetchMethod
  case object POST extends FetchMethod
}

final class FetchJsBackend[F[_]: Async](fetchMethod: FetchMethod)
    extends FetchBackend[F, FetchJsRequest] {
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
            val promise  = FetchJsBackend.methodFor(fetchMethod, request.query) match {
              case FetchMethod.POST =>
                _headers.set("Content-Type", "application/json")
                Fetch
                  .fetch(
                    baseRequest.uri.toString,
                    new RequestInit {
                      method = HttpMethod.POST
                      body = request.asJson.noSpaces
                      headers = _headers
                      signal = _signal
                    }
                  )
              case FetchMethod.GET  =>
                Fetch
                  .fetch(
                    FetchJsBackend.buildGetUri(
                      baseRequest.uri.toString,
                      request.query.value,
                      request.variables.map(_.asJson.noSpaces),
                      request.operationName
                    ),
                    new RequestInit {
                      method = HttpMethod.GET
                      headers = _headers
                      signal = _signal
                    }
                  )
            }
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
  def apply[F[_]: Async](method: FetchMethod = FetchMethod.POST): FetchJsBackend[F] =
    new FetchJsBackend[F](method)

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

  /**
   * The HTTP method for one request.
   *
   * GraphQL-over-HTTP forbids GET for a mutation operation, because an intermediary can cache, log
   * or replay a GET request. A mutation therefore goes out with POST, also when the backend is
   * configured with `FetchMethod.GET`.
   */
  private[js] def methodFor(fetchMethod: FetchMethod, query: GraphQLQuery): FetchMethod =
    fetchMethod match {
      case FetchMethod.GET if query.operationType.contains("mutation") => FetchMethod.POST
      case method                                                      => method
    }

  /**
   * Build the URL for a GraphQL GET request.
   *
   * Each query-string component is individually encoded with `encodeURIComponent`. Encoding the
   * whole URL with `encodeURI` (as was previously done) is unsafe: `encodeURI` leaves
   * URL-structural characters such as `&`, `=` and `#` untouched, so a value (e.g. inside the
   * serialized `variables` JSON) containing those characters could inject extra query parameters or
   * truncate the request at a `#` fragment.
   */
  private[js] def buildGetUri(
    baseUri:       String,
    query:         String,
    variables:     Option[String],
    operationName: Option[String]
  ): String = {
    val q    = URIUtils.encodeURIComponent(query.trim.replaceAll(" +", " "))
    val vars = variables.foldMap(v => s"&variables=${URIUtils.encodeURIComponent(v)}")
    val op   = operationName.foldMap(o => s"&operationName=${URIUtils.encodeURIComponent(o)}")
    s"$baseUri?query=$q$vars$op"
  }
}
