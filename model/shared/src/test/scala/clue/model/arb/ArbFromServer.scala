package clue.model.arb

import clue.model.StreamingMessage.FromServer
import clue.model.StreamingMessage.FromServer._
import io.circe.Json
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen

trait ArbFromServer {

  val arbErrorJson: Arbitrary[Json] =
    Arbitrary {
      arbitrary[List[(String, Int, Int)]].map { errorList =>
        Json.obj(
          "errors" -> Json.fromValues(
            errorList.map { case (msg, line, column) =>
              Json.obj(
                "message" -> Json.fromString(msg),
                "locations" -> Json.arr(
                  Json.obj(
                    "line" -> Json.fromInt(line),
                    "column" -> Json.fromInt(column)
                  )
                )
              )
            }
          )
        )
      }
    }

  val arbDataJson: Arbitrary[Json] =
    Arbitrary {
      arbitrary[String].map(Json.fromString)
    }

  implicit val arbConnectionError: Arbitrary[ConnectionError] =
    Arbitrary {
      arbitrary[Json](arbErrorJson).map(ConnectionError(_))
    }

  implicit val arbDataWrapper: Arbitrary[DataWrapper] =
    Arbitrary {
      arbitrary[Json](arbDataJson).map(DataWrapper(_))
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
