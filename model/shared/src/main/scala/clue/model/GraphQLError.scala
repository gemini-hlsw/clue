// Copyright (c) 2016-2018 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model

import cats.Eq
import clue.model.GraphQLError.Location

final case class GraphQLError(
  message:   String,
  path:      List[String],
  locations: List[Location]
)

object GraphQLError {

  final case class Location(line: Int, column: Int)

  object Location {
    implicit val EqLocation: Eq[Location] =
      Eq.by { a => (
        a.line,
        a.column
      )}
  }

  implicit val EqGraphQLError: Eq[GraphQLError] =
    Eq.by { a => (
      a.message,
      a.path,
      a.locations
    )}

}
