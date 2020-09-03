import sbt.Def
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt.librarymanagement._

object Settings {

  object LibraryVersions {
    val cats            = "2.2.0"
    val catsEffect      = "2.1.4"
    val circe           = "0.13.0"
    val disciplineMUnit = "0.2.3"
    val fs2             = "2.4.4"
    val log4Cats        = "1.1.1"
    val scalaJSDom      = "1.1.0"
    val sttpModel       = "1.1.4"
  }

  object Libraries {
    import LibraryVersions._

    val Cats = Def.setting(
      Seq(
        "org.typelevel" %%% "cats-core" % cats
      )
    )

    val CatsEffect = Def.setting(
      Seq(
        "org.typelevel" %%% "cats-effect" % catsEffect
      )
    )

    val CatsTestkit = Def.setting(
      Seq(
        "org.typelevel" %%% "cats-testkit" % cats % "test"
      )
    )

    val Circe = Def.setting(
      Seq(
        "io.circe" %%% "circe-core",
        "io.circe" %%% "circe-generic",
        "io.circe" %%% "circe-parser",
        "io.circe" %%% "circe-testing"
      ).map(_ % circe)
    )

    val DisciplineMUnit = Def.setting(
      Seq[ModuleID](
        "org.typelevel" %%% "discipline-munit" % disciplineMUnit % "test"
      )
    )

    val Fs2 = Def.setting(
      Seq(
        "co.fs2" %%% "fs2-core" % fs2
      )
    )

    val Log4Cats = Def.setting(
      Seq(
        "io.chrisdavenport" %%% "log4cats-core" % log4Cats
      )
    )

    val ScalaJSDom = Def.setting(
      Seq(
        "org.scala-js" %%% "scalajs-dom" % scalaJSDom
      )
    )

    val SttpModel = Def.setting(
      Seq(
        "com.softwaremill.sttp.model" %%% "core" % sttpModel
      )
    )
  }

}
