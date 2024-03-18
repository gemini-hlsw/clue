// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.MonadThrow
import cats.syntax.all.*
import clue.model.GraphQLRequest
import clue.model.GraphQLResponse
import clue.model.json.*
import io.circe.*
import io.circe.parser.*
import org.typelevel.log4cats.Logger

// Response format from Spec: https://github.com/APIs-guru/graphql-over-http
// {
//   "data": { ... }, // Typed
//   "errors": [ ... ]
// }
class FetchClientImpl[F[_]: MonadThrow: Logger, P, S](requestParams: P)(implicit
  backend: FetchBackend[F, P]
) extends clue.FetchClientWithPars[F, P, S] {
  override protected def requestInternal[D: Decoder, R](
    document:      String,
    operationName: Option[String],
    variables:     Option[JsonObject],
    modParams:     P => P = identity,
    errorPolicy:   ErrorPolicyProcessor[D, R]
  ): F[R] =
    backend
      .request(GraphQLRequest(document, operationName, variables), modParams(requestParams))
      .map(decode[GraphQLResponse[D]])
      .rethrow
      .flatMap(errorPolicy.process(_))
      .onError(_.warnF("Error executing query:"))
}
