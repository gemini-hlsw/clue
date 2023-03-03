// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model

import cats.Eq

final case class GraphQLDataResponse[D](data: D, errors: Option[GraphQLErrors])

object GraphQLDataResponse {
  implicit def eqGraphQLDataResponse[D: Eq]: Eq[GraphQLDataResponse[D]] =
    Eq.by(x => (x.data, x.errors))
}
