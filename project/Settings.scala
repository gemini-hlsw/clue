import sbt.Def
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt.librarymanagement._

object Settings {

  object LibraryVersions {
    val cats            = "2.6.0"
    val catsEffect      = "3.1.0"
    val circe           = "0.13.0"
    val disciplineMUnit = "1.0.8"
    val fs2             = "3.0.2"
    val grackle         = "0.0.44"
    val jawn            = "1.1.1"
    val log4Cats        = "2.0.1"
    val monocle         = "2.1.0"
    val scalaFix        = scalafix.sbt.BuildInfo.scalafixVersion
    val scalaJSDom      = "1.1.0"
    val sttpModel       = "1.4.6"
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
        "io.circe" %%% "circe-generic-extras",
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

    val Grackle = Def.setting(
      Seq(
        "edu.gemini" %%% "gsp-graphql-core" % grackle
      )
    )

    val Jawn = Def.setting(
      Seq(
        "org.typelevel" %%% "jawn-ast" % jawn
      )
    )

    val Log4Cats = Def.setting(
      Seq(
        "org.typelevel" %%% "log4cats-core" % log4Cats
      )
    )

    val Monocle = Def.setting(
      Seq(
        "com.github.julien-truffaut" %%% "monocle-core"  % monocle,
        "com.github.julien-truffaut" %%% "monocle-macro" % monocle
      )
    )

    val ScalaJSDom = Def.setting(
      Seq(
        "org.scala-js" %%% "scalajs-dom" % scalaJSDom
      )
    )

    val ScalaFix = Def.setting(
      Seq(
        "ch.epfl.scala" %%% "scalafix-core" % scalaFix
      )
    )

    val ScalaFixTestkit = Def.setting(
      Seq(
        "ch.epfl.scala" %%% "scalafix-testkit" % scalaFix % "test"
      ).map(_.cross(CrossVersion.full))
    )

    val SttpModel = Def.setting(
      Seq(
        "com.softwaremill.sttp.model" %%% "core" % sttpModel
      )
    )
  }

}
