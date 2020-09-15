package clue.macros

import cats._
import io.circe._

case class SchemaMeta(imports: List[String], mappings: Map[String, String])

object SchemaMeta {
  val Empty: SchemaMeta = SchemaMeta(List.empty, Map.empty)

  val Default: SchemaMeta                   =
    SchemaMeta(List.empty, Map("ID" -> "String", "uuid" -> "java.util.UUID"))

  implicit val eqSchemaMeta: Eq[SchemaMeta] = Eq.fromUniversalEquals

  implicit val showSchemaMeta: Show[SchemaMeta] = Show.fromToString

  implicit val monoidSchemaMeta: Monoid[SchemaMeta] = new Monoid[SchemaMeta] {
    override def empty: SchemaMeta = Empty

    override def combine(x: SchemaMeta, y: SchemaMeta): SchemaMeta =
      SchemaMeta(x.imports ++ y.imports, x.mappings ++ y.mappings)
  }

  implicit val decoderSchemaMeta: Decoder[SchemaMeta] = new Decoder[SchemaMeta] {
    final def apply(c: HCursor): Decoder.Result[SchemaMeta] =
      for {
        imports  <- c.downField("imports").as[Option[List[String]]]
        mappings <- c.downField("mappings").as[Option[Map[String, String]]]
      } yield new SchemaMeta(imports.getOrElse(List.empty), mappings.getOrElse(Map.empty))
  }
}
