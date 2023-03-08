// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.http4s

import cats.effect._
import clue._
import clue.model.GraphQLRequest
import clue.model.json._
import io.circe.Encoder
import io.circe.syntax._
import org.http4s.Request
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl

final class Http4sHttpBackend[F[_]: Concurrent](val client: Client[F])
    extends FetchBackend[F, Request[F]] {

  object dsl extends Http4sClientDsl[F]

  override def request[V: Encoder](
    request:     GraphQLRequest[V],
    baseRequest: Request[F]
  ): F[String] =
    client.expect[String](
      baseRequest.withEntity(request.asJson)
      // POST(request.asJson, uri, headers).withContentType(`Content-Type`(MediaType.application.json))
    )
}

object Http4sHttpBackend {
  def apply[F[_]: Concurrent](client: Client[F]): Http4sHttpBackend[F] =
    new Http4sHttpBackend[F](client)
}
