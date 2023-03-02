// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model

import cats.Eq
import cats.data.Ior
import cats.data.NonEmptyList

final case class GraphQLResponse[D](result: Ior[NonEmptyList[GraphQLError], D])

object GraphQLResponse {
  implicit def EqGraphQLResponse[D: Eq]: Eq[GraphQLResponse[D]] =
    Eq.by(_.result)
}
