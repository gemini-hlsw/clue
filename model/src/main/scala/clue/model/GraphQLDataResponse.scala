// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model

import cats.Eq
import cats.data.NonEmptyList

final case class GraphQLDataResponse[D](data: D, errors: Option[NonEmptyList[GraphQLError]])

object GraphQLDataResponse {
  implicit def EqGraphQLDataResponse[D: Eq]: Eq[GraphQLDataResponse[D]] =
    Eq.by(x => (x.data, x.errors))
}
