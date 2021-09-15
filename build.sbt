import sbtcrossproject.CrossPlugin.autoImport.crossProject

inThisBuild(
  List(
    scalaVersion := "3.0.2",
    homepage := Some(url("https://github.com/gemini-hlsw/clue")),
    Global / onChangedBuildSource := ReloadOnSourceChanges,
    testFrameworks += new TestFramework("munit.Framework")
  ) ++ lucumaPublishSettings
)

lazy val root = project
  .in(file("."))
  .aggregate(modelJVM,
             modelJS,
             coreJVM,
             coreJS,
             scalaJS,
             http4sJDK,
             genRules,
             genInput,
             genOutput,
             genTests
  )
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
        Settings.Libraries.Http4sCore.value ++
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

lazy val http4sJDK = project
  .in(file("http4s-jdk"))
  .settings(
    moduleName := "clue-http4s-jdk-client",
    libraryDependencies ++=
      Settings.Libraries.Http4sCirce.value ++
        Settings.Libraries.Http4sJDKClient.value
  )
  .dependsOn(coreJVM)

lazy val http4sJDKDemo = project
  .in(file("http4s-jdk-demo"))
  .settings(
    moduleName := "clue-http4s-jdk-client-demo",
    publish := false,
    libraryDependencies ++= Seq(
      "org.typelevel" %% "log4cats-slf4j" % Settings.LibraryVersions.log4Cats,
      "org.slf4j"      % "slf4j-simple"   % "1.6.4"
    )
  )
  .dependsOn(http4sJDK)

lazy val genRules = project
  .in(file("gen/rules"))
  .settings(
    moduleName := "clue-generator",
    libraryDependencies ++=
      Settings.Libraries.Grackle.value ++
        Settings.Libraries.ScalaFix.value ++
        Settings.Libraries.DisciplineMUnit.value
  )
  .dependsOn(coreJVM)

// Only necessary to fix inputs in place. Sometimes it gives a clearer picture than a diff.
// ThisBuild / scalafixScalaBinaryVersion :=
//   CrossVersion.binaryScalaVersion(scalaVersion.value)

lazy val genInput = project
  .in(file("gen/input"))
  .settings(
    publish / skip := true,
    libraryDependencies ++=
      Settings.Libraries.Monocle.value
  )
  .dependsOn(coreJVM)
//.dependsOn(genRules % ScalafixConfig) // Only necessary to fix inputs in place.

lazy val genOutput = project
  .in(file("gen/output"))
  .settings(
    publish / skip := true,
    scalacOptions += "-Wconf:cat=unused:info",
    libraryDependencies ++= Settings.Libraries.Monocle.value
  )
  .dependsOn(coreJVM)

lazy val genTests = project
  .in(file("gen/tests"))
  .settings(
    publish / skip := true,
    libraryDependencies ++= Settings.Libraries.ScalaFixTestkit.value,
    scalafixTestkitOutputSourceDirectories :=
      (genOutput / Compile / sourceDirectories).value,
    scalafixTestkitInputSourceDirectories :=
      (genInput / Compile / sourceDirectories).value,
    scalafixTestkitInputClasspath :=
      (genInput / Compile / fullClasspath).value :+
        Attributed.blank((genInput / Compile / semanticdbTargetRoot).value),
    Compile / compile :=
      (Compile / compile)
        .dependsOn(genInput / Compile / compile)
        .value
  )
  .dependsOn(genRules)
  .enablePlugins(ScalafixTestkitPlugin)
