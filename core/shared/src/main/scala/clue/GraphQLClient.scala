// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.syntax.all._
import io.circe._
import io.circe.syntax._

/**
 * A client that allows one-shot queries and mutations.
 */
trait GraphQLClient[F[_], S] {
  def request(
    operation:     GraphQLOperation[S],
    operationName: Option[String] = None
  ): RequestApplied[operation.Variables, operation.Data] = {
    import operation.implicits._
    RequestApplied(operation, operationName)
  }

  case class RequestApplied[V, D] private (
    operation:           GraphQLOperation[S],
    operationName:       Option[String]
  )(implicit varEncoder: Encoder[V], dataDecoder: Decoder[D]) {
    def apply(variables: V): F[D] =
      requestInternal[D](operation.document, operationName, variables.asJson.some)

    def apply: F[D] =
      requestInternal(operation.document, operationName, none)
  }
  object RequestApplied {
    implicit def withoutVariables[V, D](applied: RequestApplied[V, D]): F[D] = applied.apply
  }

  protected def requestInternal[D: Decoder](
    document:      String,
    operationName: Option[String] = None,
    variables:     Option[Json] = None
  ): F[D]
}
