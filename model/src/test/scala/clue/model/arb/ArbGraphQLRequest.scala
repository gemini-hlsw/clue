// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model.arb

import clue.model.GraphQLQuery
import clue.model.GraphQLRequest
import org.scalacheck.*
import org.scalacheck.Arbitrary.*

trait ArbGraphQLRequest:
  given [V: Arbitrary]: Arbitrary[GraphQLRequest[V]] =
    Arbitrary:
      for
        q <- arbitrary[String].map(GraphQLQuery(_))
        o <- arbitrary[Option[String]]
        v <- arbitrary[Option[V]]
      yield GraphQLRequest(q, o, v)

object ArbGraphQLRequest extends ArbGraphQLRequest
