package clue

import clue.macros._
import munit._
import io.circe.parser.decode
import scala.annotation.compileTimeOnly

class MacroTest extends FunSuite {

  @QueryData("clue.macros.Mappings")
  object HelloQuery extends GraphQLQuery {
    val document = """
        name
        age
        height
      """
  }

  test("macros!") {
    val json = """
      {
        "name": "John",
        "age": 2,
        "height": 3.0
      }
    """
    println(decode[HelloQuery.Data](json))
  }
}
