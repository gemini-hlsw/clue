import sbt.Def
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt.librarymanagement._

object Settings {

  object LibraryVersions {
    val cats            = "2.6.1"
    val catsEffect      = "3.2.9"
    val circe           = "0.14.1"
    val disciplineMUnit = "1.0.9"
    val fs2             = "3.1.4"
    val grackle         = "0.1.9"
    val http4s          = "1.0.0-M27"
    val http4sJDKClient = "0.6.0-M5"
    val jawn            = "1.1.1"
    val log4Cats        = "2.1.1"
    val monocle         = "3.1.0"
    val scalaFix        = scalafix.sbt.BuildInfo.scalafixVersion
    val scalaJSDom      = "1.2.0"
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

    val Grackle = Def.setting(
      Seq(
        "edu.gemini" %%% "gsp-graphql-core" % grackle
      )
    )

    val Http4sCirce = Def.setting(
      Seq(
        "org.http4s" %%% "http4s-circe" % http4s
      )
    )

    val Http4sJDKClient = Def.setting(
      Seq(
        "org.http4s" %%% "http4s-jdk-http-client" % http4sJDKClient
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
        "dev.optics" %%% "monocle-core"  % monocle,
        "dev.optics" %%% "monocle-macro" % monocle
      )
    )

    val ScalaJSDom = Def.setting(
      Seq(
        ("org.scala-js" %%% "scalajs-dom" % scalaJSDom).cross(CrossVersion.for3Use2_13)
      )
    )

    val ScalaFix = Def.setting(
      Seq(
        ("ch.epfl.scala" %%% "scalafix-core" % scalaFix)
      )
    )

    val ScalaFixTestkit = Def.setting(
      Seq(
        "ch.epfl.scala" %%% "scalafix-testkit" % scalaFix % "test"
      ).map(_.cross(CrossVersion.full))
    )

    val Http4sCore = Def.setting(
      Seq(
        "org.http4s" %%% "http4s-core" % http4s
      )
    )
  }

}
