// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import clue.model.GraphQLRequest
import sttp.model.Uri

trait Backend[F[_]] {
  def request(
    uri:     Uri,
    request: GraphQLRequest
  ): F[String]
}

object Backend {
  def apply[F[_]: Backend]: Backend[F] = implicitly
}
