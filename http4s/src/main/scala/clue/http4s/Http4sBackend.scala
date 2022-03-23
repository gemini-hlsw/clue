// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.http4s

import cats.effect._
import clue._
import clue.model.GraphQLRequest
import clue.model.json._
import io.circe.syntax._
import org.http4s.MediaType
import org.http4s.Method._
import org.http4s.Uri
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers._
import org.http4s.Headers

final class Http4sBackend[F[_]: Concurrent](val client: Client[F]) extends TransactionalBackend[F] {

  object dsl extends Http4sClientDsl[F]
  import dsl._

  def request(
    uri:     Uri,
    request: GraphQLRequest,
    headers: Headers
  ): F[String] =
    client.expect[String](
      POST(request.asJson, uri, headers).withContentType(`Content-Type`(MediaType.application.json))
    )
}

object Http4sBackend {
  def apply[F[_]: Concurrent](client: Client[F]): Http4sBackend[F] =
    new Http4sBackend[F](client)
}
