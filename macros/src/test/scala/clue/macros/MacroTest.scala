// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.macros

import clue._
import munit._
import io.circe.parser.decode
import io.circe.syntax._
import java.util.UUID
import io.circe.JsonObject

class MacroTest extends FunSuite {

  import Schemas._

  @GraphQL(debug = false)
  object LucumaTestSubscription extends GraphQLOperation[LucumaODB] {
    val document = """
      |subscription {
      |  targetEdited {
      |    id
      |    oldValue {
      |      name
      |    }
      |    newValue {
      |      name
      |    }
      |  }
      |}""".stripMargin
  }

  test("Lucuma ODB subscription macro") {
    val json         = """
      {
        "targetEdited": {
          "id": 6,
          "oldValue": {
            "name": "M51"
          },
          "newValue": {
            "name": "Betelgeuse"
          }
        }
      }
      """
    val data         = decode[LucumaTestSubscription.Data](json)
    import LucumaTestSubscription._
    val expectedData = Right(
      Data(
        Data.TargetEdited(6,
                          Data.TargetEdited.OldValue("M51"),
                          Data.TargetEdited.NewValue("Betelgeuse")
        )
      )
    )
    assertEquals(data, expectedData)
  }

  @GraphQL(debug = false)
  object ExploreQuery extends AnyRef with GraphQLOperation[Explore] {

    val document = """
      |query ($id: uuid!) {
      |  targets(where: {id: {_eq: $id}}) {
      |    id
      |    name
      |    objType: object_type
      |    ra
      |    dec
      |  }
      |  observations {
      |    id
      |  }
      |}
      """.stripMargin
  }

  test("Explore query macro") {
    val json = """
      {
        "targets": [
          {
            "id": "b9acf8b4-79e9-4c69-9a96-904746e127ab",
            "name": "NGC 1055",
            "objType": "Sidereal",
            "ra": "02:41:45.232999",
            "dec": "+00:26:35.450016"
          },
          {
            "id": "165cc9d7-0430-46a7-bebd-377bad83c184",
            "name": "NGC 7752",
            "objType": "Sidereal",
            "ra": "23:46:58.557000",
            "dec": "+29:27:32.169996"
          },
          {
            "id": "68f56259-c09d-4553-b6bc-d999205aeb59",
            "name": "NGC 1087",
            "objType": "Sidereal",
            "ra": "02:46:25.154457",
            "dec": "-00:29:55.449960"
          }
        ],
        "observations": [
          {
            "id": "e892547a-8a9c-4fed-b676-cbb1d6a0241d"
          },
          {
            "id": "b320d288-b26d-4893-b2ca-4e57eca182e7"
          }
        ]
      }
      """
    val data = decode[ExploreQuery.Data](json)

    import ExploreQuery._
    val expectedData = Right(
      Data(
        List(
          Data.Targets(UUID.fromString("b9acf8b4-79e9-4c69-9a96-904746e127ab"),
                       "NGC 1055",
                       "Sidereal",
                       "02:41:45.232999",
                       "+00:26:35.450016"
          ),
          Data.Targets(UUID.fromString("165cc9d7-0430-46a7-bebd-377bad83c184"),
                       "NGC 7752",
                       "Sidereal",
                       "23:46:58.557000",
                       "+29:27:32.169996"
          ),
          Data.Targets(UUID.fromString("68f56259-c09d-4553-b6bc-d999205aeb59"),
                       "NGC 1087",
                       "Sidereal",
                       "02:46:25.154457",
                       "-00:29:55.449960"
          )
        ),
        List(Data.Observations(UUID.fromString("e892547a-8a9c-4fed-b676-cbb1d6a0241d")),
             Data.Observations(UUID.fromString("b320d288-b26d-4893-b2ca-4e57eca182e7"))
        )
      )
    )

    assertEquals(data, expectedData)

    val variables         = Variables(UUID.fromString("a67b5e3d-7377-4a6b-a353-19fa13d8d404")).asJson
    val expectedVariables = JsonObject("id" -> "a67b5e3d-7377-4a6b-a353-19fa13d8d404".asJson).asJson
    assertEquals(variables, expectedVariables)
  }

  @GraphQL(debug = false)
  object BasicQuery extends GraphQLOperation[StarWars] {
    val document = """
        query ($charId: ID!) {
          character(id: $charId) {
            id
            name
            friends {
              id
              name
              friends {
                name
              }
            }
            more_friends {
              name
            }
          }
        }
      """
  }

  test("StarWars query macro") {
    val json = """
      {
        "character": {
          "id": "001",
          "name": "Luke",
          "friends": [
            {
              "id": "002",
              "name": "R2D2",
              "friends": [
                {
                  "name": "Rey"
                }
              ]
            },
            {
              "id": "003", 
              "name": "C3P0",
              "friends": [
                {
                  "name": "Chewie"
                }
              ]
            }
          ],
          "more_friends": [
            {
              "name": "Han"
            },
            {
              "name": "Leia"
            }
          ]
        }
      }
    """

    val data = decode[BasicQuery.Data](json)

    import BasicQuery._
    val expectedData = Right(
      Data(
        Some(
          Data.Character(
            "001",
            Some("Luke"),
            Some(
              List(
                Data.Character.Friends("002",
                                       Some("R2D2"),
                                       Some(List(Data.Character.Friends.Friends(Some("Rey"))))
                ),
                Data.Character.Friends("003",
                                       Some("C3P0"),
                                       Some(List(Data.Character.Friends.Friends(Some("Chewie"))))
                )
              )
            ),
            Some(
              List(Data.Character.MoreFriends(Some("Han")),
                   Data.Character.MoreFriends(Some("Leia"))
              )
            )
          )
        )
      )
    )

    assertEquals(data, expectedData)
  }

}
