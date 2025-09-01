// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model.arb

import cats.data.NonEmptyList
import clue.model.GraphQLError
import clue.model.GraphQLExtensions
import clue.model.GraphQLResponse
import clue.model.StreamingMessage.FromServer
import clue.model.StreamingMessage.FromServer.*
import io.circe.Json
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Gen

trait ArbFromServer:
  import ArbJson.*
  import ArbGraphQLResponse.given

  given Arbitrary[Json] =
    Arbitrary:
      arbitrary[List[(String, Int, Int)]].map: errorList =>
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

  given Arbitrary[GraphQLError.PathElement] =
    Arbitrary:
      Gen.oneOf(
        arbitrary[Int].map(GraphQLError.PathElement.int(_)),
        arbitrary[String].map(GraphQLError.PathElement.string(_))
      )

  given Arbitrary[GraphQLError.Location] =
    Arbitrary:
      for
        line   <- arbitrary[Int]
        column <- arbitrary[Int]
      yield GraphQLError.Location(line, column)

  given Arbitrary[GraphQLError] =
    Arbitrary:
      for
        message    <- arbitrary[String]
        path       <- arbitrary[List[GraphQLError.PathElement]]
        locations  <- arbitrary[List[GraphQLError.Location]]
        extensions <- arbitrary[Option[GraphQLExtensions]]
      yield GraphQLError(
        message,
        NonEmptyList.fromList(path),
        NonEmptyList.fromList(locations),
        extensions
      )

  given Arbitrary[ConnectionAck] =
    Arbitrary:
      for p <- arbitrary[Option[Map[String, Json]]](using arbOptJsonStringMap)
      yield ConnectionAck(p)

  given Arbitrary[Ping] =
    Arbitrary:
      for p <- arbitrary[Option[Map[String, Json]]](using arbOptJsonStringMap)
      yield Ping(p)

  given Arbitrary[Next] =
    Arbitrary:
      for
        i <- arbitrary[String]
        p <- arbitrary[GraphQLResponse[Json]](using arbCombinedResponse(using arbJsonString))
      yield Next(i, p)

  given Arbitrary[Error] =
    Arbitrary:
      for
        i <- arbitrary[String]
        p <- arbitrary[List[GraphQLError]].suchThat(_.nonEmpty)
      yield Error(i, NonEmptyList.fromListUnsafe(p))

  given Arbitrary[Complete] =
    Arbitrary:
      arbitrary[String].map(Complete(_))

  given Arbitrary[FromServer] =
    Arbitrary:
      Gen.oneOf[FromServer](
        arbitrary[ConnectionAck],
        arbitrary[Ping],
        arbitrary[Next],
        arbitrary[Error],
        arbitrary[Complete]
      )

object ArbFromServer extends ArbFromServer
