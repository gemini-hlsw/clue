// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model

import cats.Eq
import cats.syntax.option.*

/**
 * A GraphQL response with data.
 *
 * For a general response that may or may not contain data, use `GraphQLResponse`.
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
  errors:     Option[GraphQLErrors] = none,
  extensions: Option[GraphQLExtensions] = none
)

object GraphQLDataResponse:
  given [D: Eq]: Eq[GraphQLDataResponse[D]] =
    Eq.by(x => (x.data, x.errors, x.extensions))
