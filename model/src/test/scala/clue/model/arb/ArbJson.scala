// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model.arb

import cats.syntax.option.*
import io.circe.Json
import io.circe.JsonObject
import io.circe.syntax.*
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Gen

trait ArbJson:
  val arbJsonString: Arbitrary[Json] =
    Arbitrary:
      arbitrary[String].map(Json.fromString)

  val genJsonStringJsonTuple: Gen[(String, Json)] =
    for
      str     <- arbitrary[String]
      jsonStr <- arbitrary[Json](using arbJsonString)
    yield (str, jsonStr)

  val arbJsonObject: Arbitrary[JsonObject] =
    Arbitrary:
      Gen.mapOf[String, Json](genJsonStringJsonTuple).map(_.asJsonObject)

  val arbOptJsonObject: Arbitrary[Option[JsonObject]] =
    Arbitrary:
      Gen.frequency(
        1 -> Gen.const(none),
        9 -> arbJsonObject.arbitrary.map(_.some)
      )

object ArbJson extends ArbJson
