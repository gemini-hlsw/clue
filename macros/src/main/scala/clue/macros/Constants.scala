package clue.macros

import cats.syntax.all._
import edu.gemini.grackle.Type
import edu.gemini.grackle.ScalarType

object Constants {
  val TypeSelect = "__typename"

  val DefaultMappings: Map[String, String] =
    Map("ID" -> "String", "uuid" -> "java.util.UUID")

  val MetaTypes: Map[String, Type]         =
    // "__schema" | "__type"
    Map(TypeSelect -> ScalarType("String", "Type Discriminator".some))
}
