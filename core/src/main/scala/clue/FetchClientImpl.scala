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
import org.typelevel.log4cats.Logger

// Response format from Spec: https://github.com/APIs-guru/graphql-over-http
// {
//   "data": { ... }, // Typed
//   "errors": [ ... ]
// }
class FetchClientImpl[F[_]: MonadThrow: Logger, P, S](requestParams: C)(implicit
  backend: FetchBackend[F, P]
) extends clue.FetchClient[F, S] {
  override protected def requestInternal[D: Decoder, R](
    document:      String,
    operationName: Option[String] = None,
    variables:     Option[JsonObject] = None,
    errorPolicy:   ErrorPolicyProcessor[D, R]
  ): F[R] =
    backend
      .request(GraphQLRequest(document, operationName, variables), requestParams)
      .map(decode[GraphQLCombinedResponse[D]])
      .rethrow
      .flatMap(errorPolicy.process(_))
      .onError(_.warnF("Error executing query:"))
}
