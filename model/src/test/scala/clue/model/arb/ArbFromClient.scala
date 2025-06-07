// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model.arb

import clue.model.GraphQLRequest
import clue.model.StreamingMessage.FromClient
import clue.model.StreamingMessage.FromClient.*
import io.circe.Json
import io.circe.JsonObject
import io.circe.testing.instances.*
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Gen

trait ArbFromClient {
  import ArbGraphQLRequest._
  import ArbJson._

  implicit val arbConnectionInit: Arbitrary[ConnectionInit] =
    Arbitrary {
      arbitrary[Map[String, Json]](arbJsonStringMap).map(ConnectionInit(_))
    }

  implicit val arbStart: Arbitrary[Start] =
    Arbitrary {
      for {
        i <- arbitrary[String]
        p <- arbitrary[GraphQLRequest[JsonObject]]
      } yield Start(i, p)
    }

  implicit val arbStop: Arbitrary[Stop] =
    Arbitrary {
      arbitrary[String].map(Stop(_))
    }

  implicit val arbFromClient: Arbitrary[FromClient] =
    Arbitrary {
      Gen.oneOf[FromClient](
        arbitrary[ConnectionInit],
        arbitrary[Start],
        arbitrary[Stop],
        Gen.const(ConnectionTerminate)
      )
    }

}

object ArbFromClient extends ArbFromClient
