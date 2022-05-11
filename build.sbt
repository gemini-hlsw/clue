lazy val V = _root_.scalafix.sbt.BuildInfo

lazy val scala2Version      = V.scala213
lazy val scala3Version      = "3.1.2"
lazy val rulesCrossVersions = Seq(V.scala213)
lazy val allVersions        = rulesCrossVersions :+ scala3Version

ThisBuild / tlBaseVersion              := "0.22"
ThisBuild / tlCiReleaseBranches        := Seq("master")
ThisBuild / tlJdkRelease               := Some(8)
ThisBuild / githubWorkflowJavaVersions := Seq("11", "17").map(JavaSpec.temurin(_))
ThisBuild / scalaVersion               := scala2Version
ThisBuild / crossScalaVersions         := allVersions
Global / onChangedBuildSource          := ReloadOnSourceChanges

lazy val root = tlCrossRootProject
  .aggregate(
    model,
    core,
    scalaJS,
    http4s,
    genRules,
    genInput,
    genOutput,
    genTests
  )
  .settings(
    name := "clue"
  )

lazy val model =
  crossProject(JVMPlatform, JSPlatform)
    .crossType(CrossType.Pure)
    .in(file("model"))
    .settings(
      moduleName := "clue-model",
      libraryDependencies ++=
        Settings.Libraries.Cats.value ++
          Settings.Libraries.CatsTestkit.value ++
          Settings.Libraries.Circe.value ++
          Settings.Libraries.DisciplineMUnit.value ++
          Settings.Libraries.MUnit.value
    )

lazy val core =
  crossProject(JVMPlatform, JSPlatform)
    .crossType(CrossType.Pure)
    .in(file("core"))
    .settings(
      moduleName := "clue-core",
      libraryDependencies ++=
        Settings.Libraries.Cats.value ++
          Settings.Libraries.CatsEffect.value ++
          Settings.Libraries.Fs2.value ++
          Settings.Libraries.Log4Cats.value ++
          Settings.Libraries.Http4sCore.value ++
          Settings.Libraries.DisciplineMUnit.value ++
          Settings.Libraries.MUnit.value,
      scalacOptions ++= { if (tlIsScala3.value) Nil else List("-language:implicitConversions") }
    )
    .dependsOn(model)

lazy val scalaJS = project
  .in(file("scalajs"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    moduleName      := "clue-scalajs",
    coverageEnabled := false,
    libraryDependencies ++=
      Settings.Libraries.ScalaJSDom.value ++
        Settings.Libraries.Http4sDom.value ++
        Settings.Libraries.ScalaJSMacrotaskExecutor.value
  )
  .dependsOn(core.js)

lazy val http4s =
  crossProject(JVMPlatform, JSPlatform)
    .crossType(CrossType.Pure)
    .in(file("http4s"))
    .settings(
      moduleName := "clue-http4s",
      libraryDependencies ++=
        Settings.Libraries.Http4sCirce.value ++
          Settings.Libraries.Http4sClient.value
    )
    .dependsOn(core)

lazy val http4sJDKDemo = project
  .in(file("http4s-jdk-demo"))
  .enablePlugins(NoPublishPlugin)
  .settings(
    moduleName   := "clue-http4s-jdk-client-demo",
    tlJdkRelease := Some(11),
    libraryDependencies ++= Seq(
      "org.typelevel" %% "log4cats-slf4j" % Settings.LibraryVersions.log4Cats,
      "org.slf4j"      % "slf4j-simple"   % "1.6.4"
    ) ++ Settings.Libraries.Http4sJDKClient.value
  )
  .dependsOn(http4s.jvm)

lazy val genRules =
  project
    .in(file("gen/rules"))
    .settings(
      moduleName         := "clue-generator",
      crossScalaVersions := rulesCrossVersions,
      libraryDependencies ++=
        Settings.Libraries.Grackle.value ++
          Settings.Libraries.ScalaFix.value ++
          Settings.Libraries.DisciplineMUnit.value ++
          Settings.Libraries.MUnit.value,
      scalacOptions ~= (_.filterNot(Set("-Vtype-diffs")))
    )

// Only necessary to fix inputs in place. Sometimes it gives a clearer picture than a diff.
// ThisBuild / scalafixScalaBinaryVersion :=
//   CrossVersion.binaryScalaVersion(scalaVersion.value)

lazy val genInput =
  project
    .in(file("gen/input"))
    .enablePlugins(NoPublishPlugin)
    .settings(
      libraryDependencies ++=
        Settings.Libraries.Monocle.value
    )
    .dependsOn(core.jvm)
// .dependsOn(genRules % ScalafixConfig) // Only necessary to fix inputs in place.

lazy val genOutput = project
  .in(file("gen/output"))
  .enablePlugins(NoPublishPlugin)
  .settings(
    scalacOptions ++= { if (tlIsScala3.value) Nil else List("-Wconf:cat=unused:info") },
    libraryDependencies ++= Settings.Libraries.Monocle.value,
    tlFatalWarnings := false
  )
  .dependsOn(core.jvm)

lazy val genTests = project
  .in(file("gen/tests"))
  .enablePlugins(ScalafixTestkitPlugin, NoPublishPlugin)
  .settings(
    libraryDependencies ~= (_.filterNot(_.name == "scalafix-testkit")),
    libraryDependencies ++= Settings.Libraries.ScalaFixTestkit.value
      .map(_.cross(CrossVersion.constant(V.scala213))),
    scalafixTestkitOutputSourceDirectories := (genOutput / Compile / unmanagedSourceDirectories).value,
    scalafixTestkitInputSourceDirectories  := (genInput / Compile / unmanagedSourceDirectories).value,
    scalafixTestkitInputClasspath          := (genInput / Compile / fullClasspath).value,
    scalafixTestkitInputScalacOptions      := (genInput / Compile / scalacOptions).value,
    scalafixTestkitInputScalaVersion       := (genInput / Compile / scalaVersion).value
  )
  .dependsOn(genRules)
