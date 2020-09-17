// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.Applicative
import cats.MonadError
import cats.syntax.all._
import clue.model.GraphQLRequest
import io.chrisdavenport.log4cats.Logger
import io.circe._
import io.circe.parser._
import sttp.model.Uri

trait Backend[F[_]] {
  def request(
    uri:     Uri,
    request: GraphQLRequest
  ): F[String]
}

object Backend {
  def apply[F[_]: Backend]: Backend[F] = implicitly
}

// Response format from Spec: https://github.com/APIs-guru/graphql-over-http
// {
//   "data": { ... }, // Typed
//   "errors": [ ... ]
// }
class HttpClient[F[_]: Logger: Backend, S](uri: Uri)(implicit me: MonadError[F, Throwable])
    extends GraphQLClient[F, S] {
  private val LogPrefix = "[clue.HttpClient]"

  override protected def requestInternal[D: Decoder](
    document:      String,
    operationName: Option[String] = None,
    variables:     Option[Json] = None
  ): F[D] =
    Backend[F]
      .request(uri, GraphQLRequest(document, operationName, variables))
      .map { response =>
        parse(response).flatMap { json =>
          val cursor = json.hcursor
          cursor
            .get[List[Json]]("errors")
            .map(errors => new GraphQLException(errors))
            .swap
            .flatMap(_ => cursor.get[D]("data"))
        }
      }
      .rethrow
      .onError { case t: Throwable => Logger[F].error(t)(s"$LogPrefix Error in query: ") }
}

object HttpClient {
  def of[F[_]: Logger: Backend, S](uri: Uri)(implicit
    me:                                 MonadError[F, Throwable]
  ): F[HttpClient[F, S]] =
    Applicative[F].pure(new HttpClient[F, S](uri))
}
