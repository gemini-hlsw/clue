// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model.arb

import cats.syntax.option.*
import io.circe.Json
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

  val arbJsonStringMap: Arbitrary[Map[String, Json]] =
    Arbitrary:
      Gen.mapOf[String, Json](genJsonStringJsonTuple)

  val arbOptJsonStringMap: Arbitrary[Option[Map[String, Json]]] =
    Arbitrary:
      Gen.oneOf(
        Gen.const(none),
        arbJsonStringMap.arbitrary.map(_.some)
      )

object ArbJson extends ArbJson
