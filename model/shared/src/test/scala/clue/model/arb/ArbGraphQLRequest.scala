// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model.arb

import clue.model.GraphQLRequest
import io.circe._
import org.scalacheck._
import org.scalacheck.Arbitrary._

trait ArbGraphQLRequest {

  val arbVariables: Arbitrary[Json] =
    Arbitrary {
      arbitrary[List[(String, String)]].map { lst =>
        val kvs = lst.map { case (k, v) => (k, Json.fromString(v)) }
        Json.fromJsonObject(JsonObject.fromMap(kvs.toMap))
      }
    }

  implicit val arbGraphQLRequest: Arbitrary[GraphQLRequest] =
    Arbitrary {
      for {
        q <- arbitrary[String]
        o <- arbitrary[Option[String]]
        v <- arbitrary[Option[Json]](arbOption(arbVariables))
      } yield GraphQLRequest(q, o, v)
    }

}

object ArbGraphQLRequest extends ArbGraphQLRequest


