package clue.macros

import clue._
import munit._
import io.circe.parser.decode
import scala.annotation.compileTimeOnly

class MacroTest extends FunSuite {

  @QueryTypes("explore-simple", true)
  object ExploreSubscription extends GraphQLQuery {
    val document = """
      query ($id: uuid!) {
        targets(where: {id: {_eq: $id}}) {
          id
          name
          object_type
          ra
          dec
        }
      }
      """
  }

  // TODO: LIST or OPTION types in root Data type

  test("Explore query macro") {
    val json = """
      {
      "targets": 
        {
          "id": "b9acf8b4-79e9-4c69-9a96-904746e127ab",
          "name": "NGC 1055",
          "object_type": "Sidereal",
          "ra": "02:41:45.232999",
          "dec": "+00:26:35.450016"
        }
      }
      """
    println(decode[ExploreSubscription.Data](json))
  }

  // @QueryTypes("starwars", true)
  // object BasicQuery extends GraphQLQuery {
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

  // test("Query macro") {
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
