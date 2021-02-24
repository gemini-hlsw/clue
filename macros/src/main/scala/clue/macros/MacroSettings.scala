// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.macros

import cats._
import cats.syntax.all._
import java.io.File
import scala.util.matching.Regex

protected[macros] case class MacroSettings(
  schemaDirs:              List[File] = List.empty,
  catsEq:                  Boolean = false,
  catsShow:                Boolean = false,
  monocleLenses:           Boolean = false,
  scalajsReactReusability: Boolean = false
)

protected[macros] object MacroSettings {
  val Empty: MacroSettings = MacroSettings()

  implicit val eqSchemaMeta: Eq[MacroSettings] = Eq.fromUniversalEquals

  implicit val showSchemaMeta: Show[MacroSettings] = Show.fromToString

  implicit val monoidSchemaMeta: Monoid[MacroSettings] = new Monoid[MacroSettings] {
    override def empty: MacroSettings = Empty

    override def combine(x: MacroSettings, y: MacroSettings): MacroSettings =
      MacroSettings(
        x.schemaDirs ++ y.schemaDirs,
        x.catsEq || y.catsEq,
        x.catsShow || y.catsShow,
        x.monocleLenses || y.monocleLenses,
        x.scalajsReactReusability || y.scalajsReactReusability
      )
  }

  protected[macros] val OptionPrefix = "clue"

  private def optionRegex(option:   String, valueRegex: String): Regex =
    s"\\s*$OptionPrefix\\.$option\\s*=\\s*$valueRegex".r
  private def stringOption(option:  String): Regex = optionRegex(option, "(.*)")
  private def booleanOption(option: String): Regex = optionRegex(option, "(?i:true)")

  private val SchemaDir               = stringOption("schemaDir")
  private val CatsEq                  = booleanOption("cats\\.eq")
  private val CatsShow                = booleanOption("cats\\.show")
  private val MonocleLenses           = booleanOption("monocle\\.lenses")
  private val ScalajsReactReusability = booleanOption("scalajs-react\\.reusability")

  def fromCtxSettings(settings: List[String]): MacroSettings =
    settings.collectFold {
      case SchemaDir(schemaDir)      => MacroSettings(schemaDirs = List(new File(schemaDir.trim)))
      case CatsEq()                  => MacroSettings(catsEq = true)
      case CatsShow()                => MacroSettings(catsShow = true)
      case MonocleLenses()           => MacroSettings(monocleLenses = true)
      case ScalajsReactReusability() => MacroSettings(scalajsReactReusability = true)
    }
}
