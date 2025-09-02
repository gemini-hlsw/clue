// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model.arb

import clue.model.GraphQLRequest
import clue.model.StreamingMessage.FromClient
import clue.model.StreamingMessage.FromClient.*
import io.circe.JsonObject
import io.circe.testing.instances.*
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Gen

trait ArbFromClient:
  import ArbGraphQLRequest.given
  import ArbJson.*

  given Arbitrary[ConnectionInit] =
    Arbitrary:
      arbitrary[Option[JsonObject]](using arbOptJsonObject).map(ConnectionInit(_))

  given Arbitrary[Pong] =
    Arbitrary:
      arbitrary[Option[JsonObject]](using arbOptJsonObject).map(Pong(_))

  given Arbitrary[Subscribe] =
    Arbitrary:
      for
        i <- arbitrary[String]
        p <- arbitrary[GraphQLRequest[JsonObject]]
      yield Subscribe(i, p)

  given Arbitrary[Complete] =
    Arbitrary:
      arbitrary[String].map(Complete(_))

  given Arbitrary[FromClient] =
    Arbitrary:
      Gen.oneOf[FromClient](
        arbitrary[ConnectionInit],
        arbitrary[Pong],
        arbitrary[Subscribe],
        arbitrary[Complete]
      )

object ArbFromClient extends ArbFromClient
