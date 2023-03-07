// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.MonadThrow
import cats.syntax.all._
import clue.ErrorPolicyProcessor
import clue.model.GraphQLCombinedResponse
import clue.model.GraphQLRequest
import clue.model.json._
import io.circe._
import io.circe.parser._
import org.http4s.Headers
import org.http4s.Uri
import org.typelevel.log4cats.Logger

// Response format from Spec: https://github.com/APIs-guru/graphql-over-http
// {
//   "data": { ... }, // Typed
//   "errors": [ ... ]
// }
class TransactionalClientImpl[F[_]: MonadThrow: TransactionalBackend: Logger, S](
  uri:     Uri,
  headers: Headers
) extends clue.TransactionalClient[F, S] {
  override protected def requestInternal[D: Decoder, R](
    document:      String,
    operationName: Option[String] = None,
    variables:     Option[JsonObject] = None,
    errorPolicy:   ErrorPolicyProcessor[D, R]
  ): F[R] =
    TransactionalBackend[F]
      .request(uri, GraphQLRequest(document, operationName, variables), headers)
      .map(decode[GraphQLCombinedResponse[D]])
      .rethrow
      .flatMap(errorPolicy.process(_))
      .onError(_.warnF("Error executing query:"))
}
