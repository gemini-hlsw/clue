lazy val V = _root_.scalafix.sbt.BuildInfo

lazy val scala2Version      = V.scala213
lazy val scala3Version      = "3.1.0"
lazy val rulesCrossVersions = Seq(V.scala213)
lazy val allVersions        = rulesCrossVersions :+ scala3Version

inThisBuild(
  List(
    scalaVersion                  := scala2Version,
    homepage                      := Some(url("https://github.com/gemini-hlsw/clue")),
    Global / onChangedBuildSource := ReloadOnSourceChanges,
    testFrameworks += new TestFramework("munit.Framework")
  ) ++ lucumaPublishSettings
)

lazy val root = project
  .in(file("."))
  .aggregate(
    model.projectRefs ++
      core.projectRefs ++
      scalaJS.projectRefs ++
      http4sJDK.projectRefs ++
      genRules.projectRefs ++
      genInput.projectRefs ++
      genOutput.projectRefs ++
      genTests.projectRefs: _*
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
      Settings.Libraries.ScalaJSDom.value ++
        Settings.Libraries.Http4sDom.value ++
        Settings.Libraries.ScalaJSMacrotaskExecutor.value
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
          Settings.Libraries.DisciplineMUnit.value,
      scalacOptions ~= (_.filterNot(Set("-Vtype-diffs")))
    )
    .dependsOn(core)
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
    // .dependsOn(genRules % ScalafixConfig) // Only necessary to fix inputs in place.
    .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaPartialVersion(scala3Version))
    .jvmPlatform(allVersions)

lazy val genOutput = projectMatrix
  .in(file("gen/output"))
  .settings(
    publish / skip := true,
    scalacOptions ++=
      (scalaVersion.value match {
        case `scala3Version` => Nil
        case _               => List("-Wconf:cat=unused:info")
      }),
    libraryDependencies ++= Settings.Libraries.Monocle.value
  )
  .dependsOn(core)
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaPartialVersion(scala3Version))
  .jvmPlatform(allVersions)

lazy val genTestsAggregate = Project("genTests", file("target/genTestsAggregate"))
  .aggregate(genTests.projectRefs: _*)

lazy val genTests = projectMatrix
  .in(file("gen/tests"))
  .settings(
    publish / skip                         := true,
    libraryDependencies ++= Settings.Libraries.ScalaFixTestkit.value,
    scalafixTestkitOutputSourceDirectories :=
      TargetAxis
        .resolve(genOutput, Compile / unmanagedSourceDirectories)
        .value,
    scalafixTestkitInputSourceDirectories  :=
      TargetAxis
        .resolve(genInput, Compile / unmanagedSourceDirectories)
        .value,
    scalafixTestkitInputClasspath          :=
      TargetAxis.resolve(genInput, Compile / fullClasspath).value,
    scalafixTestkitInputScalacOptions      :=
      TargetAxis.resolve(genInput, Compile / scalacOptions).value,
    scalafixTestkitInputScalaVersion       :=
      TargetAxis.resolve(genInput, Compile / scalaVersion).value
  )
  .dependsOn(genRules)
  .enablePlugins(ScalafixTestkitPlugin)
  .defaultAxes(
    rulesCrossVersions.map(VirtualAxis.scalaABIVersion) :+ VirtualAxis.jvm: _*
  )
  .customRow(
    scalaVersions = Seq(V.scala213),
    axisValues = Seq(TargetAxis(scala3Version), VirtualAxis.jvm),
    settings = Seq()
  )
  .customRow(
    scalaVersions = Seq(V.scala213),
    axisValues = Seq(TargetAxis(V.scala213), VirtualAxis.jvm),
    settings = Seq()
  )
