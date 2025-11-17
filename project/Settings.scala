import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt.Def
import sbt.librarymanagement._

object Settings {

  object LibraryVersions {
    val cats                     = "2.13.0"
    val catsEffect               = "3.6.3"
    val circe                    = "0.14.14"
    val disciplineMUnit          = "2.0.0"
    val fs2                      = "3.12.2"
    val grackle                  = "0.25.0"
    val http4s                   = "0.23.33"
    val http4sDom                = "0.2.7"
    val http4sJDKClient          = "0.10.0"
    val jawn                     = "1.3.2"
    val kittens                  = "3.5.0"
    val log4Cats                 = "2.7.1"
    val monocle                  = "3.3.0"
    val munit                    = "1.1.1"
    val munitCatsEffect          = "2.1.0"
    val natchez                  = "0.3.8"
    val scalaFix                 = scalafix.sbt.BuildInfo.scalafixVersion
    val scalaJsDom               = "2.8.1"
    val scalaJsMacrotaskExecutor = "1.1.1"
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
        "org.typelevel" %%% "grackle-core" % grackle
      )
    )

    val Http4sCirce = Def.setting(
      Seq(
        "org.http4s" %%% "http4s-circe" % http4s
      )
    )

    val Http4sCore = Def.setting(
      Seq(
        "org.http4s" %%% "http4s-core" % http4s
      )
    )

    val Http4sClient = Def.setting(
      Seq(
        "org.http4s" %%% "http4s-client" % http4s
      )
    )

    val Http4sDom = Def.setting(
      Seq(
        "org.http4s" %%% "http4s-dom" % http4sDom
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

    val Kittens = Def.setting(
      Seq(
        "org.typelevel" %%% "kittens" % kittens
      )
    )

    val Log4Cats = Def.setting(
      Seq(
        "org.typelevel" %%% "log4cats-core"    % log4Cats,
        "org.typelevel" %%% "log4cats-testing" % log4Cats % "test"
      )
    )

    val Monocle = Def.setting(
      Seq(
        "dev.optics" %%% "monocle-core"  % monocle,
        "dev.optics" %%% "monocle-macro" % monocle
      )
    )

    val MonocleLaw = Def.setting(
      Seq(
        "dev.optics" %%% "monocle-law" % monocle
      )
    )

    val MUnit = Def.setting(
      Seq[ModuleID](
        "org.scalameta" %%% "munit" % munit % "test"
      )
    )

    val MUnitCatsEffect = Def.setting(
      Seq[ModuleID](
        "org.typelevel" %%% "munit-cats-effect" % munitCatsEffect % "test"
      )
    )

    val Natchez = Def.setting(
      Seq(
        "org.tpolecat" %%% "natchez-core" % natchez
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

    val ScalaJsDom = Def.setting(
      Seq(
        "org.scala-js" %%% "scalajs-dom" % scalaJsDom
      )
    )

    val ScalaJsMacrotaskExecutor = Def.setting(
      Seq(
        "org.scala-js" %%% "scala-js-macrotask-executor" % scalaJsMacrotaskExecutor
      )
    )

  }

}
