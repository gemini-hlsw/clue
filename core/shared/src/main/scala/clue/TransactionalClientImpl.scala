// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.MonadError
import cats.syntax.all._
import clue.model.GraphQLRequest
import io.chrisdavenport.log4cats.Logger
import io.circe._
import io.circe.parser._
import sttp.model.Uri

// Response format from Spec: https://github.com/APIs-guru/graphql-over-http
// {
//   "data": { ... }, // Typed
//   "errors": [ ... ]
// }
class TransactionalClientImpl[F[_]: MonadError[*[_], Throwable]: TransactionalBackend: Logger, S](
  uri: Uri
) extends clue.TransactionalClient[F, S] {
  override protected def requestInternal[D: Decoder](
    document:      String,
    operationName: Option[String] = None,
    variables:     Option[Json] = None
  ): F[D] =
    TransactionalBackend[F]
      .request(uri, GraphQLRequest(document, operationName, variables))
      .map { response =>
        parse(response).flatMap { json =>
          val cursor = json.hcursor
          cursor
            .get[List[Json]]("errors")
            .map(errors => new GraphQLException(errors.toString))
            .swap
            .flatMap(_ => cursor.get[D]("data"))
        }
      }
      .rethrow
      .onError { case t: Throwable => t.logF("Error in query: ") }
}
