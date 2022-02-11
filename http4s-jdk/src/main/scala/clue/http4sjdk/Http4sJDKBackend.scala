// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.http4sjdk

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
import org.http4s.jdkhttpclient.JdkHttpClient

import java.net.http.HttpClient
import org.http4s.Headers

final class Http4sJDKBackend[F[_]: Async](val client: Client[F]) extends TransactionalBackend[F] {

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

object Http4sJDKBackend {
  def apply[F[_]: Async]: Resource[F, Http4sJDKBackend[F]] =
    JdkHttpClient.simple[F].map(new Http4sJDKBackend[F](_))

  def fromHttpClient[F[_]: Async](client: HttpClient): Resource[F, Http4sJDKBackend[F]] =
    JdkHttpClient[F](client).map(new Http4sJDKBackend(_))

}
