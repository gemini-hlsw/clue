// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model
package arb

import org.scalacheck._
import org.scalacheck.Arbitrary._

trait ArbGraphQLError {

  import GraphQLError.Location

  implicit val arbGraphQLErrorLocation: Arbitrary[Location] =
    Arbitrary {
      for {
        line   <- arbitrary[Int]
        column <- arbitrary[Int]
      } yield Location(line, column)
    }

  implicit val arbGraphQLError: Arbitrary[GraphQLError] =
    Arbitrary {
      for {
        message   <- arbitrary[String]
        path      <- arbitrary[List[String]]
        locations <- arbitrary[List[Location]]
      } yield GraphQLError(message, path, locations)
    }

}

object ArbGraphQLError extends ArbGraphQLError
