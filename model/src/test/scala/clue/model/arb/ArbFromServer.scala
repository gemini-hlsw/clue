// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model.arb

import cats.data.NonEmptyList
import clue.model.GraphQLDataResponse
import clue.model.GraphQLError
import clue.model.StreamingMessage.FromServer
import clue.model.StreamingMessage.FromServer._
import io.circe.Json
import io.circe.testing.instances._
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen

import clue.model.GraphQLExtensions
import org.scalacheck.Arbitrary

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

  implicit val arbGraphQLErrorPathElement: Arbitrary[GraphQLError.PathElement] =
    Arbitrary(
      Gen.oneOf(
        arbitrary[Int].map(GraphQLError.PathElement.int(_)),
        arbitrary[String].map(GraphQLError.PathElement.string(_))
      )
    )

  implicit val arbGraphQLErrorLocation: Arbitrary[GraphQLError.Location] =
    Arbitrary(
      for {
        line   <- arbitrary[Int]
        column <- arbitrary[Int]
      } yield GraphQLError.Location(line, column)
    )

  implicit val arbGraphQLError: Arbitrary[GraphQLError] =
    Arbitrary {
      for {
        message    <- arbitrary[String]
        path       <- arbitrary[List[GraphQLError.PathElement]]
        locations  <- arbitrary[List[GraphQLError.Location]]
        extensions <- arbitrary[Option[GraphQLExtensions]]
      } yield GraphQLError(
        message,
        NonEmptyList.fromList(path),
        NonEmptyList.fromList(locations),
        extensions
      )
    }

  implicit val arbConnectionError: Arbitrary[ConnectionError] =
    Arbitrary {
      arbitrary[Json](arbJsonString).map(ConnectionError(_))
    }

  implicit val arbDataWrapper: Arbitrary[GraphQLDataResponse[Json]] =
    Arbitrary {
      for {
        data       <- arbitrary[Json](arbJsonString)
        errors     <- arbitrary[List[GraphQLError]]
        extensions <- arbitrary[Option[GraphQLExtensions]]
      } yield GraphQLDataResponse(data, NonEmptyList.fromList(errors), extensions)
    }

  implicit val arbData: Arbitrary[Data] =
    Arbitrary {
      for {
        i <- arbitrary[String]
        p <- arbitrary[GraphQLDataResponse[Json]]
      } yield Data(i, p)
    }

  implicit val arbError: Arbitrary[Error] =
    Arbitrary {
      for {
        i <- arbitrary[String]
        p <- arbitrary[List[GraphQLError]].suchThat(_.nonEmpty)
      } yield Error(i, NonEmptyList.fromListUnsafe(p))
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
