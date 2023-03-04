// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model
package arb

import org.scalacheck.Arbitrary._
import org.scalacheck._
import cats.data.NonEmptyList
import io.circe.testing.instances._

trait ArbGraphQLError {

  import GraphQLError.{Location, PathElement}

  implicit val arbPathElement: Arbitrary[PathElement] =
    Arbitrary {
      Gen.oneOf(
        arbitrary[Int].map(PathElement.int),
        arbitrary[String].map(PathElement.string)
      )
    }

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
        message    <- arbitrary[String]
        path       <- arbitrary[List[PathElement]]
        locations  <- arbitrary[List[Location]]
        extensions <- arbitrary[Option[GraphQLExtensions]]
      } yield GraphQLError(
        message,
        NonEmptyList.fromList(path),
        NonEmptyList.fromList(locations),
        extensions
      )
    }

}

object ArbGraphQLError extends ArbGraphQLError
