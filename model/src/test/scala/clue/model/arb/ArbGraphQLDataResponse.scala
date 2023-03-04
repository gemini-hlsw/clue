// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model.arb

import clue.model.GraphQLDataResponse
import clue.model.GraphQLExtensions
import org.scalacheck.Arbitrary._
import org.scalacheck._
import clue.model.GraphQLError
import clue.model.arb.ArbGraphQLError._
import io.circe.testing.instances._
import cats.data.NonEmptyList

trait ArbGraphQLDataResponse {

  implicit def arbGraphQLDataResponse[D: Arbitrary]: Arbitrary[GraphQLDataResponse[D]] =
    Arbitrary {
      for {
        d <- arbitrary[D]
        e <- arbitrary[List[GraphQLError]]
        x <- arbitrary[Option[GraphQLExtensions]]
      } yield GraphQLDataResponse(d, NonEmptyList.fromList(e), x)
    }

}

object ArbGraphQLDataResponse extends ArbGraphQLDataResponse
