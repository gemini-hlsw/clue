import sbtcrossproject.CrossPlugin.autoImport.crossProject

Global / onChangedBuildSource := ReloadOnSourceChanges

inThisBuild(List(
  name := "clue",
  scalaVersion := "2.13.1",
  crossScalaVersions := Seq("2.12.10", "2.13.1"),
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-Xfatal-warnings",
      "-encoding", "UTF-8",
      "-Ymacro-annotations"
    ),  
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
  scmInfo := Some(ScmInfo(
    url("https://https://github.com/rpiaggio/clue"),
    "scm:git:git@github.com:rpiaggio/clue.git",
    Some("scm:git:git@github.com:rpiaggio/clue.git"))),
  pomIncludeRepository := { _ => false }
))

lazy val root = project.in(file("."))
  .aggregate(coreJVM, coreJS, scalaJS)
  .settings(
    publish := {},
    publishLocal := {}
  )

lazy val core = crossProject(JVMPlatform, JSPlatform).in(file("core"))
  .settings(
    moduleName := "clue-core",
    libraryDependencies ++=
      Settings.Libraries.CatsJS.value ++
        Settings.Libraries.CatsEffectJS.value ++
        Settings.Libraries.Fs2JS.value ++
        Settings.Libraries.Circe.value ++
        Settings.Libraries.Log4Cats.value    
  )
  .jsSettings(
    scalacOptions ++= Seq(
      "-P:scalajs:suppressMissingJSGlobalDeprecations"
    )
  )

lazy val coreJS = core.js

lazy val coreJVM = core.jvm

lazy val scalaJS = project.in(file("scalajs"))
  .settings(
    moduleName := "clue-scalajs",
    libraryDependencies ++=
      Settings.Libraries.ScalaJSDom.value,
/*      // Settings.Libraries.CatsJS.value ++
        Settings.Libraries.CatsEffectJS.value ++
        // Settings.Libraries.Fs2JS.value ++
        Settings.Libraries.Circe.value ++
        Settings.Libraries.Log4Cats.value,*/
    scalacOptions ++= Seq(
      "-P:scalajs:suppressMissingJSGlobalDeprecations"
    )
  )
  .dependsOn(coreJS)
  .enablePlugins(ScalaJSPlugin)

sonatypeProfileName := "com.rpiaggio"

packagedArtifacts in root := Map.empty