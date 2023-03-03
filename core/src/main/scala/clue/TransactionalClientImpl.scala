// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.effect.Sync
import cats.syntax.all._
import clue.model.GraphQLRequest
import clue.model.GraphQLTransactionalResponse
import clue.model.json._
import io.circe._
import io.circe.parser._
import org.http4s.Headers
import org.http4s.Uri
import org.typelevel.log4cats.Logger

import clue.ErrorPolicyProcessor
// Response format from Spec: https://github.com/APIs-guru/graphql-over-http
// {
//   "data": { ... }, // Typed
//   "errors": [ ... ]
// }
class TransactionalClientImpl[F[_]: Sync: TransactionalBackend: Logger, S](
  uri:     Uri,
  headers: Headers
) extends clue.TransactionalClient[F, S] {
  override protected def requestInternal[D: Decoder, R](
    document:      String,
    operationName: Option[String] = None,
    variables:     Option[Json] = None,
    errorPolicy:   ErrorPolicyProcessor[D, R]
  ): F[R] =
    TransactionalBackend[F]
      .request(uri, GraphQLRequest(document, operationName, variables), headers)
      .map(decode[GraphQLTransactionalResponse[D]])
      .rethrow
      .flatMap(response => errorPolicy.process(response.result))
      .onError(_.warnF("Error executing query:"))
}
