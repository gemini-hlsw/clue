package clue.macros

import scala.annotation.compileTimeOnly

// @compileTimeOnly("Mappings are only needed at compile time")
trait Mapping {
  val mapping: Map[String, String]
}

// @compileTimeOnly("Only needed at compile time")
object Mappings extends Mapping {
  val mapping: Map[String, String] = Map("name" -> "String", "age" -> "Int", "height" -> "Double")
}
