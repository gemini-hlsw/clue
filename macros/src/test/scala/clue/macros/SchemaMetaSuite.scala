package clue.macros

import munit._
import cats.syntax.all._
import cats.kernel.laws.discipline.MonoidTests
import org.scalacheck.Gen
import org.scalacheck.Gen._
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary._
import cats.instances.map
import io.circe.parser.decode

class SchemaMetaSuite extends DisciplineSuite {
  val genSchemaMeta: Gen[SchemaMeta] =
    for {
      imports  <- Gen.listOfN(5, arbitrary[String])
      mappings <- Gen.mapOfN(5, arbitrary[(String, String)])
    } yield SchemaMeta(imports, mappings)

  implicit val arbitrarySchemaMeta: Arbitrary[SchemaMeta] = Arbitrary(genSchemaMeta)

  checkAll("Monoid[SchemaMeta]", MonoidTests[SchemaMeta].monoid)

  test("Decoder") {
    val json = """
      |{
      |  "imports": [
      |    "java.util._"
      |  ],
      |  "mappings": {
      |    "uuid": "UUID",
      |    "map":  "TreeMap[String, String]"
      |  }
      |}""".stripMargin
    assertEquals(
      decode[SchemaMeta](json),
      SchemaMeta(List("java.util._"),
                 Map("uuid" -> "UUID", "map" -> "TreeMap[String, String]")
      ).asRight
    )
  }

  test("Decoder 2") {
    val json = """
      |{
      |  "mappings": {
      |    "uuid": "java.util.UUID",
      |    "map":  "java.util.TreeMap[String, String]"
      |  }
      |}""".stripMargin
    assertEquals(
      decode[SchemaMeta](json),
      SchemaMeta(Nil,
                 Map("uuid" -> "java.util.UUID", "map" -> "java.util.TreeMap[String, String]")
      ).asRight
    )
  }
}
