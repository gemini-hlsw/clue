package clue

import munit._
import io.circe.parser.decode

class MacroTest extends FunSuite {

  @QueryData
  object HelloQuery extends GraphQLQuery {
    val document = """
        name
        age
        height
      """
  }

  test("macros!") {
    // println(HelloQuery.Data(1, 2, 3))
    val json = """
      {
        "name": 1,
        "age": 2,
        "height": 3
      }
    """
    println(decode[HelloQuery.Data](json))
  }
}
