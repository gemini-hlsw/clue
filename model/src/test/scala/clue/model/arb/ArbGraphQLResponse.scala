// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model.arb

import cats.data.Ior
import clue.model.GraphQLErrors
import clue.model.GraphQLExtensions
import clue.model.GraphQLResponse
import clue.model.arb.ArbGraphQLError.*
import io.circe.testing.instances.*
import org.scalacheck.*
import org.scalacheck.Arbitrary.*

trait ArbGraphQLResponse {

  implicit def arbIor[A: Arbitrary, B: Arbitrary]: Arbitrary[Ior[A, B]] =
    Arbitrary {
      Gen.oneOf(
        arbitrary[A].map(Ior.left(_)),
        arbitrary[B].map(Ior.right(_)),
        for {
          a <- arbitrary[A]
          b <- arbitrary[B]
        } yield Ior.both(a, b)
      )
    }

  implicit def arbCombinedResponse[D: Arbitrary]: Arbitrary[GraphQLResponse[D]] =
    Arbitrary {
      for {
        result     <- arbitrary[Ior[GraphQLErrors, D]]
        extensions <- arbitrary[Option[GraphQLExtensions]]
      } yield GraphQLResponse(result, extensions)
    }

}

object ArbGraphQLResponse extends ArbGraphQLResponse
