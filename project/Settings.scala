import sbt.Def
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt.librarymanagement._

object Settings {

  object LibraryVersions {
    val cats                        = "2.1.0"
    val catsEffect                  = "2.1.0"
    val fs2                         = "2.2.2"
    val circe                       = "0.12.3"
    val circeGenericExtras          = "0.12.2"
    val log4Cats                    = "1.0.1"
    val log4CatsLog4s               = "0.4.0-M1"
    val scalaJSDom                  = "1.0.0"
    val sttpModel                   = "1.0.2"
  }

  object Libraries {
    import LibraryVersions._

    val CatsJS = Def.setting(Seq(
      "org.typelevel" %%% "cats-core" % cats
    ))

    val CatsEffectJS = Def.setting(Seq(
      "org.typelevel" %%% "cats-effect" % catsEffect
    ))

    val Fs2JS = Def.setting(Seq(
      "co.fs2" %%% "fs2-core" % fs2
    ))

    val Circe = Def.setting(Seq(
          "io.circe" %%% "circe-core",
          "io.circe" %%% "circe-generic",
          "io.circe" %%% "circe-parser"
      ).map(_ % circe) ++ Seq(
        "io.circe" %%% "circe-generic-extras" % circeGenericExtras
      )
    )

    val Log4Cats = Def.setting(Seq(
      "io.chrisdavenport" %%% "log4cats-core" % log4Cats,
      "io.chrisdavenport" %%% "log4cats-log4s" % log4CatsLog4s
    ))

    val ScalaJSDom = Def.setting(Seq(
      "org.scala-js" %%% "scalajs-dom" % scalaJSDom
    ))

    val SttpModel = Def.setting(Seq(
      "com.softwaremill.sttp.model" %%% "core" % sttpModel
    ))
  }

}