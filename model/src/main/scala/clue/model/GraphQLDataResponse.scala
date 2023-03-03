// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model

import cats.Eq
import io.circe.Json

/**
 * A GraphQL response with data.
 *
 * For a general response that may or may not contain data, use `GraphQLCombinedResponse`.
 *
 * See https://spec.graphql.org/October2021/#sec-Response-Format
 *
 * @param data
 *   request result
 * @param errors
 *   possible errors raised by the request
 * @param extensions
 *   values for protocol extension
 */
final case class GraphQLDataResponse[D](
  data:       D,
  errors:     Option[GraphQLErrors],
  extensions: Option[GraphQLExtensions]
)

object GraphQLDataResponse {
  implicit def eqGraphQLDataResponse[D: Eq]: Eq[GraphQLDataResponse[D]] =
    Eq.by(x => (x.data, x.errors, x.extensions))
}
