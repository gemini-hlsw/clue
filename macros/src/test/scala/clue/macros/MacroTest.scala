package clue.macros

import clue._
import munit._
import io.circe.parser.decode
import scala.annotation.compileTimeOnly

class MacroTest extends FunSuite {

  @QueryTypes("starwars")
  object HelloQuery extends GraphQLQuery {
    val document = """
        query {
          character(id: $id) {
            id
            name
            friends
          }
        }
      """
  }

  test("macros!") {
    val json = """
      {
        "character": {
          "id": "001",
          "name": "Luke",
          "friends": [
            {
              "id": "002",
              "name": "R2D2"
            },
            {
              "id": "003",
              "name": "C3P0"
            }          
          ]
        }
      }
    """
    println(decode[HelloQuery.Data](json))
  }
}
