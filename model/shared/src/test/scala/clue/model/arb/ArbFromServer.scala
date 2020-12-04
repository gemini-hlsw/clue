// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model.arb

import clue.model.StreamingMessage.FromServer
import clue.model.StreamingMessage.FromServer._
import io.circe.Json
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen
import io.circe.JsonObject

trait ArbFromServer {
  import ArbJson._

  val arbErrorJson: Arbitrary[Json] =
    Arbitrary {
      arbitrary[List[(String, Int, Int)]].map { errorList =>
        Json.obj(
          "errors" -> Json.fromValues(
            errorList.map { case (msg, line, column) =>
              Json.obj(
                "message"   -> Json.fromString(msg),
                "locations" -> Json.arr(
                  Json.obj(
                    "line"   -> Json.fromInt(line),
                    "column" -> Json.fromInt(column)
                  )
                )
              )
            }
          )
        )
      }
    }

  val genErrosJson: Gen[Json]                                 =
    arbitrary[String].map(s => Json.arr(Json.obj("message" -> Json.fromString(s))))

  implicit val arbConnectionError: Arbitrary[ConnectionError] =
    Arbitrary {
      arbitrary[JsonObject](arbJsonObjectOfStrings).map(ConnectionError(_))
    }

  implicit val arbDataWrapper: Arbitrary[DataWrapper] =
    Arbitrary {
      for {
        data   <- arbitrary[Json](arbJsonString)
        errors <- Gen.option(genErrosJson)
      } yield DataWrapper(data, errors)
    }

  implicit val arbData: Arbitrary[Data] =
    Arbitrary {
      for {
        i <- arbitrary[String]
        p <- arbitrary[DataWrapper]
      } yield Data(i, p)
    }

  implicit val arbError: Arbitrary[Error] =
    Arbitrary {
      for {
        i <- arbitrary[String]
        p <- arbitrary[Json](arbErrorJson)
      } yield Error(i, p)
    }

  implicit val arbComplete: Arbitrary[Complete] =
    Arbitrary {
      arbitrary[String].map(Complete(_))
    }

  implicit val arbFromServer: Arbitrary[FromServer] =
    Arbitrary {
      Gen.oneOf[FromServer](
        Gen.const(ConnectionAck),
        arbitrary[ConnectionError],
        Gen.const(ConnectionKeepAlive),
        arbitrary[Data],
        arbitrary[Error],
        arbitrary[Complete]
      )
    }
}

object ArbFromServer extends ArbFromServer
