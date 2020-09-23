package clue.macros

import clue._
import munit._
import io.circe.parser.decode
import io.circe.syntax._
import java.{ util => ju }

class MacroTest extends FunSuite {

  import Schemas._

  @GraphQL(debug = false)
  object AddTarget extends GraphQLOperation[Schemas.Explore] {
    override val document = """
      query($target: targets_insert_input!) {
        insert_targets_one(object: $target) {
          id 
        }
      } 
    """
  }

  @GraphQL(debug = false)
  @scala.annotation.unused
  object RemoveTarget extends GraphQLOperation[Explore] {
    val document: String = """
      mutation ($id: uuid!) {
        delete_targets_by_pk(id: $id) {
          id
        }
      }
    """
  }

  @GraphQL(debug = false, mappings = Map("targetobjecttype" -> "String"))
  object ExploreSubscription extends AnyRef with GraphQLOperation[Explore] {

    val document = """
      |subscription ($id: uuid!) {
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
    val data = decode[ExploreSubscription.Data](json)
    println("*** DECODED DATA: " + data)
    println(ExploreSubscription.Data.observations.get(data.toOption.get))
    println("*** ENCODED VARIABLES: " + ExploreSubscription.Variables(ju.UUID.randomUUID()).asJson)

    println(
      decode[RemoveTarget.Data](
        """ { "delete_targets_by_pk": {"id": "e892547a-8a9c-4fed-b676-cbb1d6a0241d"} } """
      )
    )
  }

  // @GraphQL("starwars", debug = false)
  // object BasicQuery extends GraphQLOperation {
  //   val document = """
  //       query {
  //         character(id: $charId) {
  //           id
  //           name
  //           friends
  //         }
  //       }
  //     """
  // }

  // test("StarWars") {
  //   val json = """
  //     {
  //       "character": {
  //         "id": "001",
  //         "name": "Luke",
  //         "friends": [
  //           {
  //             "id": "002",
  //             "name": "R2D2"
  //           },
  //           {
  //             "id": "003",
  //             "name": "C3P0"
  //           }
  //         ]
  //       }
  //     }
  //   """
  //   println(decode[BasicQuery.Data](json))
  // }

}