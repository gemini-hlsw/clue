// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.macros

import clue._
import cats.syntax.all._
import munit._
import io.circe.parser.decode
import io.circe.syntax._
import java.util.UUID
import io.circe.JsonObject

class MacroTest extends FunSuite {

  import Schemas._

  @GraphQL(debug = false)
  object SumQuery extends GraphQLOperation[StarWars] {
    val document = """
        query ($charId: ID!) {
          character(id: $charId) {
            id
            name
            ... on Human {
              homePlanet
            }
            friends {
              name
            }
            ... on Droid {
              primaryFunction
            }
            __typename
          }
        }
      """
  }

  test("StarWars query with inline fragments macro - Human") {
    val json = """
      {
        "character": {
          "id": "001",
          "name": "Luke",
          "homePlanet": "Tatooine",
          "friends": [
            {
              "name": "R2D2"
            },
            {
              "name": "C3P0"
            }
          ],
          "__typename": "Human"
        }
      }
    """

    val data = decode[SumQuery.Data](json)

    import SumQuery._
    val expectedData = Right(
      Data(
        Data.Character
          .Human(
            "001",
            "Luke".some,
            "Tatooine".some,
            List(
              Data.Character.Friends("R2D2".some),
              Data.Character.Friends("C3P0".some)
            ).some,
            "Human"
          )
          .some
      )
    )

    assertEquals(data, expectedData)
  }

  test("StarWars query with inline fragments macro - Droid") {
    val json = """
      {
        "character": {
          "id": "002",
          "name": "R2D2",
          "friends": [
            {
              "name": "Luke"
            },
            {
              "name": "C3P0"
            }
          ],
          "primaryFunction": "Astromech",
          "__typename": "Droid"
        }
      }
    """

    val data = decode[SumQuery.Data](json)

    import SumQuery._
    val expectedData = Right(
      Data(
        Data.Character
          .Droid(
            "002",
            "R2D2".some,
            List(
              Data.Character.Friends("Luke".some),
              Data.Character.Friends("C3P0".some)
            ).some,
            "Astromech".some,
            "Droid"
          )
          .some
      )
    )

    assertEquals(data, expectedData)
  }

  @GraphQL(debug = false)
  object LucumaQuery extends GraphQLOperation[LucumaODB] {
    val document = """
      query Program {
        program(id: "p-2") {
          id
          name
          targets(includeDeleted: true) {
            id
            name
            tracking {
              tracktype: __typename 
              ... on Sidereal {
                epoch
              }
              ... on Nonsidereal {
                keyType
              }
            }
          }
        }
      }"""
  }

  test("Lucuma ODB query with inline fragments macro") {
    val json = """
      {
        "program": {
          "id": "p-1",
          "name": "Macro program",
          "targets": [
            {
              "id": "t-1",
              "name": "Sirius",
              "tracking": {
                "tracktype": "Sidereal",
                "epoch": "J2000.000"
              }
            },
            {
              "id": "t-2",
              "name": "Saturn",
              "tracking": {
                "tracktype": "Nonsidereal",
                "keyType": "MAJOR_BODY"
              }
            }
          ]
        }
      }
      """
    val data = decode[LucumaQuery.Data](json)

    import LucumaQuery._
    val expectedData = Right(
      Data(
        Data
          .Program(
            "p-1",
            "Macro program".some,
            List(
              Data.Program.Targets(
                "t-1",
                "Sirius",
                Data.Program.Targets.Tracking.Sidereal("Sidereal", "J2000.000")
              ),
              Data.Program.Targets(
                "t-2",
                "Saturn",
                Data.Program.Targets.Tracking.Nonsidereal("Nonsidereal",
                                                          LucumaODB.Enums.EphemerisKeyType.MajorBody
                )
              )
            )
          )
          .some
      )
    )

    assertEquals(data, expectedData)
  }

  @GraphQL(debug = false)
  object LucumaSiderealQuery extends GraphQLOperation[LucumaODB] {
    val document = """
      query Program {
        program(id: "p-2") {
          id
          name
          targets(includeDeleted: true) {
            id
            name
            tracking {
              ... on Sidereal {
                epoch
              }
            }
          }
        }
      }"""
  }

  test("Lucuma ODB query with single inline fragment macro") {
    val json = """
      {
        "program": {
          "id": "p-1",
          "name": "Macro program",
          "targets": [
            {
              "id": "t-1",
              "name": "Sirius",
              "tracking": {
                "epoch": "J2000.000"
              }
            }
          ]
        }
      }
      """
    val data = decode[LucumaSiderealQuery.Data](json)

    import LucumaSiderealQuery._
    val expectedData = Right(
      Data(
        Data
          .Program(
            "p-1",
            "Macro program".some,
            List(
              Data.Program.Targets(
                "t-1",
                "Sirius",
                Data.Program.Targets.Tracking("J2000.000")
              )
            )
          )
          .some
      )
    )

    assertEquals(data, expectedData)
  }

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
    val json = """
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
    val data = decode[LucumaTestSubscription.Data](json)

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
        Data
          .Character(
            "001",
            "Luke".some,
            List(
              Data.Character.Friends("002",
                                     "R2D2".some,
                                     List(Data.Character.Friends.Friends("Rey".some)).some
              ),
              Data.Character.Friends("003",
                                     "C3P0".some,
                                     List(Data.Character.Friends.Friends("Chewie".some)).some
              )
            ).some,
            List(Data.Character.MoreFriends("Han".some),
                 Data.Character.MoreFriends("Leia".some)
            ).some
          )
          .some
      )
    )

    assertEquals(data, expectedData)
  }

}
