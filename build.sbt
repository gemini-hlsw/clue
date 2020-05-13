import sbtcrossproject.CrossPlugin.autoImport.crossProject

Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val macroVersion = "2.1.1"

// shamelessly copied from monocle
lazy val paradisePlugin = Def.setting {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, v)) if v <= 12 =>
      Seq(compilerPlugin(("org.scalamacros" % "paradise" % macroVersion).cross(CrossVersion.patch)))
    case _                       =>
      // if scala 2.13.0-M4 or later, macro annotations merged into scala-reflect
      // https://github.com/scala/scala/pull/6606
      Nil
  }
}

lazy val scalaOptions = Def.setting {
  PartialFunction
    .condOpt(CrossVersion.partialVersion(scalaVersion.value)) {
      case Some((2, n)) if n <= 12 => Seq("-Xfuture", "-Yno-adapted-args", "-Ypartial-unification")
      case Some((2, n)) if n >= 13 => Seq("-Ymacro-annotations")
    }
    .toList
    .flatten
}

inThisBuild(
  List(
    name := "clue",
    scalaVersion := "2.13.2",
    crossScalaVersions := Seq("2.12.11", scalaVersion.value),
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-Xfatal-warnings",
      "-encoding",
      "UTF-8",
      "-language:higherKinds"
    ) ++ scalaOptions.value,
    organization := "com.rpiaggio",
    homepage := Some(url("https://github.com/rpiaggio/clue")),
    licenses += ("BSD 3-Clause", url("http://opensource.org/licenses/BSD-3-Clause")),
    developers := List(
      Developer(
        "rpiaggio",
        "Raúl Piaggio",
        "rpiaggio@gmail.com",
        url("http://rpiaggio.com")
      )
    ),
    scmInfo := Some(
      ScmInfo(url("https://https://github.com/rpiaggio/clue"),
              "scm:git:git@github.com:rpiaggio/clue.git",
              Some("scm:git:git@github.com:rpiaggio/clue.git")
      )
    ),
    pomIncludeRepository := { _ => false }
  )
)

lazy val root = project
  .in(file("."))
  .aggregate(coreJVM, coreJS, scalaJS)
  .settings(
    publish := {},
    publishLocal := {}
  )

lazy val core = crossProject(JVMPlatform, JSPlatform)
  .in(file("core"))
  .settings(
    moduleName := "clue-core",
    libraryDependencies ++=
      Settings.Libraries.CatsJS.value ++
        Settings.Libraries.CatsEffectJS.value ++
        Settings.Libraries.Fs2JS.value ++
        Settings.Libraries.Circe.value ++
        Settings.Libraries.Log4Cats.value ++
        Settings.Libraries.SttpModel.value ++
        paradisePlugin.value
  )

lazy val coreJS = core.js

lazy val coreJVM = core.jvm

lazy val scalaJS = project
  .in(file("scalajs"))
  .settings(
    moduleName := "clue-scalajs",
    libraryDependencies ++=
      Settings.Libraries.ScalaJSDom.value
  )
  .dependsOn(coreJS)
  .enablePlugins(ScalaJSPlugin)

sonatypeProfileName := "com.rpiaggio"

packagedArtifacts in root := Map.empty
