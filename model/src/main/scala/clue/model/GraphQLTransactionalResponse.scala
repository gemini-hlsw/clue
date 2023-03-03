// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model

import cats.Eq
import cats.data.Ior

final case class GraphQLTransactionalResponse[D](result: Ior[GraphQLErrors, D])

object GraphQLTransactionalResponse {
  implicit def EqGraphQLTransactionalResponse[D: Eq]: Eq[GraphQLTransactionalResponse[D]] =
    Eq.by(_.result)
}
