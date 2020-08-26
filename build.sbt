import sbtcrossproject.CrossPlugin.autoImport.crossProject

Global / onChangedBuildSource := ReloadOnSourceChanges

inThisBuild(
  List(
    name := "clue",
    scalaVersion := "2.13.3",
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-Xfatal-warnings",
      "-encoding",
      "UTF-8",
      "-language:higherKinds",
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
  .aggregate(modelJVM, modelJS, coreJVM, coreJS, scalaJS)
  .settings(
    publish := {},
    publishLocal := {}
  )

lazy val model = crossProject(JVMPlatform, JSPlatform)
  .in(file("model"))
  .settings(
    moduleName := "clue-model",
    libraryDependencies ++=
      Settings.Libraries.Cats.value ++
        Settings.Libraries.CatsTestkit.value ++
        Settings.Libraries.Circe.value ++
        Settings.Libraries.CirceGenericExtras.value
  )

lazy val modelJS = model.js

lazy val modelJVM = model.jvm

lazy val core = crossProject(JVMPlatform, JSPlatform)
  .in(file("core"))
  .settings(
    moduleName := "clue-core",
    libraryDependencies ++=
      Settings.Libraries.Cats.value ++
        Settings.Libraries.CatsEffect.value ++
        Settings.Libraries.Fs2.value ++
        Settings.Libraries.Log4Cats.value ++
        Settings.Libraries.SttpModel.value
  )
  .dependsOn(model)

lazy val coreJS = core.js

lazy val coreJVM = core.jvm

lazy val scalaJS = project
  .in(file("scalajs"))
  .settings(
    moduleName := "clue-scalajs",
    libraryDependencies ++=
      Settings.Libraries.CatsEffect.value ++
        Settings.Libraries.ScalaJSDom.value ++
        Settings.Libraries.Log4Cats.value ++
        Settings.Libraries.SttpModel.value
  )
  .dependsOn(coreJS)
  .enablePlugins(ScalaJSPlugin)

sonatypeProfileName := "com.rpiaggio"

packagedArtifacts in root := Map.empty
