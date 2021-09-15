// import sbtcrossproject.CrossPlugin.autoImport.crossProject

inThisBuild(
  List(
    scalaVersion                  := "3.0.2",
    crossScalaVersions ++= Seq("2.13.6", "3.0.2"),
    homepage                      := Some(url("https://github.com/gemini-hlsw/clue")),
    Global / onChangedBuildSource := ReloadOnSourceChanges,
    testFrameworks += new TestFramework("munit.Framework")
  ) ++ lucumaPublishSettings
)

lazy val V = _root_.scalafix.sbt.BuildInfo

lazy val rulesCrossVersions = Seq(V.scala213)
lazy val scala3Version      = "3.0.2"
lazy val allVersions        = rulesCrossVersions :+ scala3Version

lazy val root = project
  .in(file("."))
  .aggregate(
    model.projectRefs ++
      core.projectRefs ++
      scalaJS.projectRefs ++
      http4sJDK.projectRefs ++
      genRules.projectRefs ++
      genInput.projectRefs: _* // ++
    // genOutput.projectRefs ++
    // genTests.projectRefs: _*
  )
  .settings(
    name           := "clue",
    publish / skip := true
  )

lazy val model =
  projectMatrix
    .in(file("model"))
    .settings(
      moduleName := "clue-model",
      libraryDependencies ++=
        Settings.Libraries.Cats.value ++
          Settings.Libraries.CatsTestkit.value ++
          Settings.Libraries.Circe.value ++
          Settings.Libraries.DisciplineMUnit.value
    )
    .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaPartialVersion(scala3Version))
    .jvmPlatform(allVersions)
    .jsPlatform(allVersions,
                List(scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)))
    )

lazy val core =
  projectMatrix
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
    .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaPartialVersion(scala3Version))
    .jvmPlatform(allVersions)
    .jsPlatform(allVersions)

lazy val scalaJS = projectMatrix
  .in(file("scalajs"))
  .settings(
    moduleName := "clue-scalajs",
    libraryDependencies ++=
      Settings.Libraries.ScalaJSDom.value
  )
  .dependsOn(core)
  .defaultAxes(VirtualAxis.js, VirtualAxis.scalaPartialVersion(scala3Version))
  .jsPlatform(allVersions)

lazy val http4sJDK = projectMatrix
  .in(file("http4s-jdk"))
  .settings(
    moduleName := "clue-http4s-jdk-client",
    libraryDependencies ++=
      Settings.Libraries.Http4sCirce.value ++
        Settings.Libraries.Http4sJDKClient.value
  )
  .dependsOn(core)
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaPartialVersion(scala3Version))
  .jvmPlatform(allVersions)

lazy val http4sJDKDemo = projectMatrix
  .in(file("http4s-jdk-demo"))
  .settings(
    moduleName := "clue-http4s-jdk-client-demo",
    publish    := false,
    libraryDependencies ++= Seq(
      "org.typelevel" %% "log4cats-slf4j" % Settings.LibraryVersions.log4Cats,
      "org.slf4j"      % "slf4j-simple"   % "1.6.4"
    )
  )
  .dependsOn(http4sJDK)
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaPartialVersion(scala3Version))
  .jvmPlatform(allVersions)

lazy val genRules =
  projectMatrix
    .in(file("gen/rules"))
    .settings(
      moduleName := "clue-generator",
      libraryDependencies ++=
        Settings.Libraries.Grackle.value ++
          Settings.Libraries.ScalaFix.value ++
          Settings.Libraries.DisciplineMUnit.value
    )
    // .dependsOn(coreJVM)
    // .defaultAxes(VirtualAxis.jvm)
    .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaPartialVersion(rulesCrossVersions.head))
    .jvmPlatform(rulesCrossVersions)

// Only necessary to fix inputs in place. Sometimes it gives a clearer picture than a diff.
// ThisBuild / scalafixScalaBinaryVersion :=
//   CrossVersion.binaryScalaVersion(scalaVersion.value)

lazy val genInput =
  projectMatrix
    .in(file("gen/input"))
    .settings(
      publish / skip := true,
      libraryDependencies ++=
        Settings.Libraries.Monocle.value
    )
    .dependsOn(core)
    //.dependsOn(genRules % ScalafixConfig) // Only necessary to fix inputs in place.
    .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaPartialVersion(scala3Version))
    .jvmPlatform(allVersions)

// lazy val genOutput = project
//   .in(file("gen/output"))
//   .settings(
//     publish / skip := true,
//     scalacOptions += "-Wconf:cat=unused:info",
//     libraryDependencies ++= Settings.Libraries.Monocle.value
//   )
//   .dependsOn(coreJVM)

// lazy val genTests = project
//   .in(file("gen/tests"))
//   .settings(
//     publish / skip := true,
//     libraryDependencies ++= Settings.Libraries.ScalaFixTestkit.value,
//     scalafixTestkitOutputSourceDirectories :=
//       (genOutput / Compile / sourceDirectories).value,
//     scalafixTestkitInputSourceDirectories :=
//       (genInput / Compile / sourceDirectories).value,
//     scalafixTestkitInputClasspath :=
//       (genInput / Compile / fullClasspath).value :+
//         Attributed.blank((genInput / Compile / semanticdbTargetRoot).value),
//     Compile / compile :=
//       (Compile / compile)
//         .dependsOn(genInput / Compile / compile)
//         .value
//   )
//   .dependsOn(genRules)
//   .enablePlugins(ScalafixTestkitPlugin)
