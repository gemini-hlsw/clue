// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model.arb

import clue.model.GraphQLRequest
import io.circe._
import org.scalacheck.Arbitrary._
import org.scalacheck._

trait ArbGraphQLRequest {

  implicit val arbJsonVariables: Arbitrary[Json] =
    Arbitrary {
      arbitrary[List[(String, String)]].map { lst =>
        val kvs = lst.map { case (k, v) => (k, Json.fromString(v)) }
        Json.fromJsonObject(JsonObject.fromMap(kvs.toMap))
      }
    }

  implicit def arbGraphQLRequest[V: Arbitrary]: Arbitrary[GraphQLRequest[V]] =
    Arbitrary {
      for {
        q <- arbitrary[String]
        o <- arbitrary[Option[String]]
        v <- arbitrary[Option[V]]
      } yield GraphQLRequest(q, o, v)
    }

}

object ArbGraphQLRequest extends ArbGraphQLRequest
