lazy val V = _root_.scalafix.sbt.BuildInfo

ThisBuild / tlBaseVersion              := "0.49"
ThisBuild / tlCiReleaseBranches        := Seq("master")
ThisBuild / tlJdkRelease               := Some(8)
ThisBuild / githubWorkflowJavaVersions := Seq("11", "17").map(JavaSpec.temurin(_))
ThisBuild / scalaVersion               := "3.7.4"
Global / onChangedBuildSource          := ReloadOnSourceChanges

lazy val root = tlCrossRootProject
  .aggregate(
    model,
    core,
    scalaJS,
    http4s,
    http4sJDKDemo,
    natchez,
    genRules,
    genInput,
    genOutput,
    genTests,
    sbtPlugin
  )
  .settings(
    name := "clue"
  )

lazy val model =
  crossProject(JVMPlatform, JSPlatform)
    .crossType(CrossType.Pure)
    .in(file("model"))
    .settings(
      moduleName                              := "clue-model",
      // temporary? fix for upgrading to Scala 3.7: https://github.com/scala/scala3/issues/22890
      dependencyOverrides += "org.scala-lang" %% "scala3-library" % scalaVersion.value,
      libraryDependencies ++=
        Settings.Libraries.Cats.value ++
          Settings.Libraries.CatsTestkit.value ++
          Settings.Libraries.Circe.value ++
          Settings.Libraries.DisciplineMUnit.value ++
          Settings.Libraries.Fs2.value ++
          Settings.Libraries.Kittens.value ++
          Settings.Libraries.Log4Cats.value ++
          Settings.Libraries.Monocle.value ++
          Settings.Libraries.MonocleLaw.value ++
          Settings.Libraries.MUnit.value,
      Test / classLoaderLayeringStrategy      := ClassLoaderLayeringStrategy.Flat // Needed for circe's codec tests
    )

lazy val core =
  crossProject(JVMPlatform, JSPlatform)
    .crossType(CrossType.Pure)
    .in(file("core"))
    .settings(
      moduleName                              := "clue-core",
      // temporary? fix for upgrading to Scala 3.7: https://github.com/scala/scala3/issues/22890
      dependencyOverrides += "org.scala-lang" %% "scala3-library" % scalaVersion.value,
      libraryDependencies ++=
        Settings.Libraries.Cats.value ++
          Settings.Libraries.CatsEffect.value ++
          Settings.Libraries.Fs2.value ++
          Settings.Libraries.Log4Cats.value ++
          Settings.Libraries.DisciplineMUnit.value ++
          Settings.Libraries.MUnitCatsEffect.value ++
          Settings.Libraries.MUnit.value
    )
    .dependsOn(model)

lazy val scalaJS =
  project
    .in(file("scalajs"))
    .enablePlugins(ScalaJSPlugin)
    .settings(
      moduleName                              := "clue-scalajs",
      coverageEnabled                         := false,
      // temporary? fix for upgrading to Scala 3.7: https://github.com/scala/scala3/issues/22890
      dependencyOverrides += "org.scala-lang" %% "scala3-library" % scalaVersion.value,
      libraryDependencies ++=
        Settings.Libraries.ScalaJsDom.value ++
          Settings.Libraries.ScalaJsMacrotaskExecutor.value
    )
    .dependsOn(core.js)

lazy val http4s =
  crossProject(JVMPlatform, JSPlatform)
    .crossType(CrossType.Pure)
    .in(file("http4s"))
    .settings(
      moduleName                              := "clue-http4s",
      // temporary? fix for upgrading to Scala 3.7: https://github.com/scala/scala3/issues/22890
      dependencyOverrides += "org.scala-lang" %% "scala3-library" % scalaVersion.value,
      libraryDependencies ++=
        Settings.Libraries.Http4sCirce.value ++
          Settings.Libraries.Http4sClient.value
    )
    .dependsOn(core)

lazy val http4sJDKDemo =
  project
    .in(file("http4s-jdk-demo"))
    .enablePlugins(NoPublishPlugin)
    .settings(
      moduleName           := "clue-http4s-jdk-client-demo",
      tlJdkRelease         := Some(11),
      Compile / run / fork := true,
      libraryDependencies ++= Seq(
        "org.typelevel" %% "log4cats-slf4j" % Settings.LibraryVersions.log4Cats,
        "org.slf4j"      % "slf4j-simple"   % "2.0.17"
      ) ++ Settings.Libraries.Http4sJDKClient.value,
      scalacOptions += "-language:implicitConversions"
    )
    .dependsOn(http4s.jvm)

lazy val natchez =
  crossProject(JVMPlatform, JSPlatform)
    .crossType(CrossType.Pure)
    .in(file("natchez"))
    .settings(
      moduleName := "clue-natchez",
      libraryDependencies ++= Settings.Libraries.Natchez.value
    )
    .dependsOn(core)

lazy val genRules =
  project
    .in(file("gen/rules"))
    .settings(
      moduleName   := "clue-generator",
      scalaVersion := "2.13.17",
      libraryDependencies ++=
        Settings.Libraries.Grackle.value ++
          Settings.Libraries.ScalaFix.value ++
          Settings.Libraries.CatsTestkit.value ++
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
    .disablePlugins(ScalafixPlugin)
    .settings(
      libraryDependencies ++=
        Settings.Libraries.Monocle.value
    )
    .dependsOn(core.jvm)
// .dependsOn(genRules % ScalafixConfig) // Only necessary to fix inputs in place.

lazy val genOutput =
  project
    .in(file("gen/output"))
    .enablePlugins(NoPublishPlugin)
    .disablePlugins(ScalafixPlugin)
    .settings(
      scalacOptions ++= { if (tlIsScala3.value) Nil else List("-Wconf:cat=unused:info") },
      libraryDependencies ++= Settings.Libraries.Monocle.value,
      tlFatalWarnings := false
    )
    .dependsOn(core.jvm)

lazy val genTests =
  project
    .in(file("gen/tests"))
    .enablePlugins(ScalafixTestkitPlugin, NoPublishPlugin)
    .disablePlugins(ScalafixPlugin)
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

lazy val sbtPlugin =
  project
    .in(file("sbt-plugin"))
    .enablePlugins(SbtPlugin, BuildInfoPlugin)
    .settings(
      moduleName                              := "sbt-clue",
      crossScalaVersions                      := List("2.12.20"),
      scalacOptions                           := Nil,
      addSbtPlugin("ch.epfl.scala"      % "sbt-scalafix"      % V.scalafixVersion),
      addSbtPlugin("org.portable-scala" % "sbt-platform-deps" % "1.0.2"),
      addSbtPlugin("org.portable-scala" % "sbt-crossproject"  % "1.3.2"),
      buildInfoPackage                        := "clue.sbt",
      buildInfoKeys                           := Seq[BuildInfoKey](
        version,
        organization,
        "rulesModule" -> (genRules / moduleName).value,
        "coreModule"  -> (core.jvm / moduleName).value
      ),
      buildInfoOptions += BuildInfoOption.PackagePrivate,
      Test / test                             :=
        scripted.toTask("").value,
      scripted                                := scripted
        .dependsOn(
          genRules / publishLocal,
          model.jvm / publishLocal,
          core.jvm / publishLocal
        )
        .evaluated,
      scriptedLaunchOpts ++= Seq(
        "-Xmx1024M",
        "-Dplugin.version=" + version.value,
        "-Dscala.version=" + (core.jvm / scalaVersion).value
      ),
      scriptedBufferLog                       := false,
      // temporary? fix for upgrading to Scala 3.7: https://github.com/scala/scala3/issues/22890
      dependencyOverrides += "org.scala-lang" %% "scala3-library" % scalaVersion.value
    )
