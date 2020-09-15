import sbtcrossproject.CrossPlugin.autoImport.crossProject

inThisBuild(
  Seq(
    //
    scalacOptions += "-Ymacro-annotations",
    // scalaVersion := "2.13.3",
    scalacOptions += "-language:experimental.macros",
    //
    homepage := Some(url("https://github.com/gemini-hlsw/clue")),
    Global / onChangedBuildSource := ReloadOnSourceChanges,
    testFrameworks += new TestFramework("munit.Framework")
  ) ++ gspPublishSettings
)

lazy val root = project
  .in(file("."))
  // .aggregate(modelJVM, modelJS, coreJVM, coreJS, scalaJS, macrosJVM, macrosJS)
  .aggregate(modelJVM, modelJS, coreJVM, coreJS, scalaJS, macros)
  .settings(
    name := "clue",
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
        Settings.Libraries.Circe.value ++
        Settings.Libraries.DisciplineMUnit.value
  )
  .jsSettings(
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule))
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
        Settings.Libraries.SttpModel.value ++
        Settings.Libraries.DisciplineMUnit.value
  )
  .dependsOn(model)

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

lazy val macros =
// crossProject(JVMPlatform, JSPlatform)
  project
    .in(file("macro"))
    .settings(
      moduleName := "clue-macro",
      libraryDependencies ++=
        Settings.Libraries.DisciplineMUnit.value ++
          Settings.Libraries.Grackle.value ++
          Settings.Libraries.Monocle.value,
      scalacOptions ~= (_.filterNot(
        Set(
          // Disabling these to explore macros.
          "-Wdead-code",
          "-Wunused:params",
          "-Wunused:explicits",
          "-Wunused:implicits",
          "-Wunused:locals",
          "-Wunused:imports",
          "-Wunused:patvars",
          "-Wunused:privates",
          "-Yno-predef",
          "-Ywarn-unused-import"
        )
      ))
    )
    // .dependsOn(core)
    .dependsOn(coreJVM)

// lazy val macrosJS = macros.js

// lazy val macrosJVM = macros.jvm
