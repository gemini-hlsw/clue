package clue.macros

import cats._
import io.circe._
import org.typelevel.jawn.ast._
import scala.util.Try
import scala.util.Failure
import shapeless.Succ
import scala.util.Success

protected[macros] case class SchemaMeta(imports: List[String], mappings: Map[String, String])

protected[macros] object SchemaMeta {
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

  def fromJson(json: String): Try[SchemaMeta] =
    JParser.parseFromString(json).flatMap {
      _ match {
        case JObject(map) =>
          (map.get("imports") match {
            case None               => Success(Nil)
            case Some(JArray(list)) => Try(list.toList.map(_.asString))
            case other              => Failure(new Exception(s"Invalid Schema Meta imports: [$other]"))
          }).flatMap { imports =>
            (map.get("mappings") match {
              case None                       => Success(Map.empty[String, String])
              case Some(JObject(mappingsMap)) =>
                Try(mappingsMap.map { case (k, v) => (k, v.asString) }.toMap)
              case other                      => Failure(new Exception(s"Invalid Schema Meta mappings: [$other]"))
            }).map { mappings =>
              SchemaMeta(imports, mappings)
            }
          }
        case _            => Failure(new Exception(s"Invalid Schema Meta JSON: [$json]"))
      }
    }
}
