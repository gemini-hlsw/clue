// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model
package arb

import cats.data.NonEmptyList
import clue.model.GraphQLErrors
import io.circe.testing.instances.*
import org.scalacheck.*
import org.scalacheck.Arbitrary.*

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

  implicit val arbGraphQLErrors: Arbitrary[GraphQLErrors] =
    Arbitrary(
      arbitrary[List[GraphQLError]].withFilter(_.nonEmpty).map(NonEmptyList.fromListUnsafe)
    )

}

object ArbGraphQLError extends ArbGraphQLError
