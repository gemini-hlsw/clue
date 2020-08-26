import sbtcrossproject.CrossPlugin.autoImport.crossProject

inThisBuild(
  Seq(
    homepage := Some(url("https://github.com/gemini-hlsw/clue")),
    Global / onChangedBuildSource := ReloadOnSourceChanges
  ) ++ gspPublishSettings
)

lazy val root = project
  .in(file("."))
  .aggregate(modelJVM, modelJS, coreJVM, coreJS, scalaJS)
  .settings(
    publish := {},
    publishLocal := {},
    packagedArtifacts := Map.empty
  )

lazy val model = crossProject(JVMPlatform, JSPlatform)
  .in(file("model"))
  .settings(
    moduleName := "clue-model",
    libraryDependencies ++=
      Settings.Libraries.Cats.value ++
        Settings.Libraries.CatsTestkit.value ++
        Settings.Libraries.Circe.value
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
