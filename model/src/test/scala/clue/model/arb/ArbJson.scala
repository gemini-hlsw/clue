// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model.arb

import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary._
import io.circe.Json
import org.scalacheck.Gen

trait ArbJson {
  val arbJsonString: Arbitrary[Json] =
    Arbitrary {
      arbitrary[String].map(Json.fromString)
    }

  val genJsonStringJsonTuple: Gen[(String, Json)] =
    for {
      str     <- arbitrary[String]
      jsonStr <- arbitrary[Json](arbJsonString)
    } yield (str, jsonStr)

  val arbJsonStringMap: Arbitrary[Map[String, Json]] =
    Arbitrary {
      Gen.mapOf[String, Json](genJsonStringJsonTuple)
    }
}

object ArbJson extends ArbJson
