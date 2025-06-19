// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model.arb

import cats.data.NonEmptyList
import clue.model.GraphQLDataResponse
import clue.model.GraphQLError
import clue.model.GraphQLExtensions
import clue.model.arb.ArbGraphQLError.given
import io.circe.testing.instances.*
import org.scalacheck.*
import org.scalacheck.Arbitrary.*

trait ArbGraphQLDataResponse:
  given [D: Arbitrary]: Arbitrary[GraphQLDataResponse[D]] =
    Arbitrary:
      for
        d <- arbitrary[D]
        e <- arbitrary[List[GraphQLError]]
        x <- arbitrary[Option[GraphQLExtensions]]
      yield GraphQLDataResponse(d, NonEmptyList.fromList(e), x)

object ArbGraphQLDataResponse extends ArbGraphQLDataResponse
