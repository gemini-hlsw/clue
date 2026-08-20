// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.http4s

import cats.effect.*
import cats.syntax.all.*
import clue.*
import clue.model.GraphQLRequest
import clue.model.json.given
import io.circe.Encoder
import io.circe.syntax.*
import org.http4s.MediaType
import org.http4s.QValue
import org.http4s.Request
import org.http4s.circe.*
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers.Accept
import org.http4s.headers.`Content-Type`
import org.http4s.syntax.literals.qValue

final class Http4sHttpBackend[F[_]: Concurrent](val client: Client[F])
    extends FetchBackend[F, Request[F]] {

  object dsl extends Http4sClientDsl[F]

  private val GraphQLResponseMediaType =
    new MediaType("application", "graphql-response+json", compressible = true)

  private val AcceptHeader = Accept(
    GraphQLResponseMediaType.withQValue(QValue.One),
    MediaType.application.json.withQValue(qValue"0.9")
  )

  // The specification requires the GraphQL media type in the `Accept` header. A header that the
  // caller set stays unchanged.
  private def withAccept(request: Request[F]): Request[F] =
    if (request.headers.contains[Accept]) request
    else request.putHeaders(AcceptHeader)

  override def request[V: Encoder](
    request:     GraphQLRequest[V],
    baseRequest: Request[F]
  ): F[String] =
    client
      .run(withAccept(baseRequest.withEntity(request.asJson)))
      .use { response =>
        val contentType: Option[String] =
          response.headers.get[`Content-Type`].map(_.mediaType.show)

        response.bodyText.compile.string.flatMap { body =>
          if (GraphQLOverHttp.processBody(response.status.code, contentType))
            body.pure[F]
          else
            HttpStatusException(response.status.code, contentType, body).raiseError[F, String]
        }
      }
}

object Http4sHttpBackend {
  def apply[F[_]: Concurrent](client: Client[F]): Http4sHttpBackend[F] =
    new Http4sHttpBackend[F](client)
}
