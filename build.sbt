lazy val V = _root_.scalafix.sbt.BuildInfo

ThisBuild / tlBaseVersion               := "0.58"
ThisBuild / tlJdkRelease                := Some(17)
ThisBuild / githubWorkflowJavaVersions  := Seq("25", "17").map(JavaSpec.temurin(_))
ThisBuild / scalaVersion                := "3.8.4"
ThisBuild / crossScalaVersions          := Seq("3.8.4")
ThisBuild / githubWorkflowScalaVersions := Seq("3.8.4")
Global / onChangedBuildSource           := ReloadOnSourceChanges

// The CI matrix covers Scala 3 only, which builds `sbt-clue` for sbt 2.x. Run the scripted tests
// of the sbt 1.x cross-build (Scala 2.12) in a separate step.
ThisBuild / githubWorkflowBuild += WorkflowStep.Sbt(
  List("++ 2.12.20", "sbtPlugin/test"),
  name = Some("Test the sbt plugin against sbt 1.x"),
  cond = Some("matrix.project == 'rootJVM'")
)

// sbt-typelevel-ci hardcodes Java 11 (both the job's `javas` matrix and the baked-in
// `matrix.java == 'temurin@11'` cond on its Setup Java step) for its auto-added
// "validate-steward" job, but the scala-steward binary that coursier/setup-action
// installs is now built for a newer JVM (class file version 61 = Java 17), so that job
// fails with UnsupportedClassVersionError. Rebuild the job on Java 17 until the plugin
// catches up.
ThisBuild / githubWorkflowAddedJobs ~= { jobs =>
  jobs.map { job =>
    if (job.id == "validate-steward")
      WorkflowJob(
        "validate-steward",
        "Validate Steward Config",
        WorkflowStep.Checkout ::
          WorkflowStep.SetupJava(List(JavaSpec.temurin("17")), false) :::
          WorkflowStep.Use(
            UseRef.Public("coursier", "setup-action", "v1"),
            Map("apps" -> "scala-steward")
          ) ::
          WorkflowStep.Run(List("scala-steward validate-repo-config .scala-steward.conf")) :: Nil,
        scalas = List.empty,
        javas = List(JavaSpec.temurin("17"))
      )
    else job
  }
}

lazy val root = tlCrossRootProject
  .aggregate(
    model,
    core,
    scalaJS,
    http4s,
    http4sJDKDemo,
    otel4s,
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
      moduleName                         := "clue-model",
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
      Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat // Needed for circe's codec tests
    )

lazy val core =
  crossProject(JVMPlatform, JSPlatform)
    .crossType(CrossType.Pure)
    .in(file("core"))
    .enablePlugins(BuildInfoPlugin)
    .settings(
      moduleName       := "clue-core",
      buildInfoPackage := "clue",
      buildInfoKeys    := Seq[BuildInfoKey](name, version),
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
      moduleName := "clue-scalajs",
      libraryDependencies ++=
        Settings.Libraries.ScalaJsDom.value ++
          Settings.Libraries.ScalaJsMacrotaskExecutor.value ++
          Settings.Libraries.MUnit.value
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
          Settings.Libraries.Http4sClient.value ++
          Settings.Libraries.Http4sOtel4sMiddleware.value ++
          Settings.Libraries.MUnitCatsEffect.value ++
          Settings.Libraries.MUnit.value
    )
    .dependsOn(core)

lazy val http4sJDKDemo =
  project
    .in(file("http4s-jdk-demo"))
    .enablePlugins(NoPublishPlugin)
    .settings(
      moduleName           := "clue-http4s-jdk-client-demo",
      tlJdkRelease         := Some(17),
      Compile / run / fork := true,
      libraryDependencies ++= Seq(
        "org.typelevel" %% "log4cats-slf4j" % Settings.LibraryVersions.log4Cats,
        "org.slf4j"      % "slf4j-simple"   % "2.0.18"
      ) ++ Settings.Libraries.Http4sJDKClient.value,
      scalacOptions += "-language:implicitConversions"
    )
    .dependsOn(http4s.jvm)

lazy val otel4s =
  crossProject(JVMPlatform, JSPlatform)
    .crossType(CrossType.Pure)
    .in(file("otel4s"))
    .settings(
      moduleName := "clue-otel4s",
      libraryDependencies ++=
        Settings.Libraries.Otel4s.value ++
          Settings.Libraries.MUnit.value
    )
    .dependsOn(core)

lazy val genRules =
  project
    .in(file("gen/rules"))
    .settings(
      moduleName   := "clue-generator",
      scalaVersion := "2.13.18",
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
      moduleName                           := "sbt-clue",
      scalaVersion                         := "2.12.20",
      crossScalaVersions                   := List("2.12.20", "3.8.4"),
      scalacOptions                        := Nil,
      (pluginCrossBuild / sbtVersion)      := {
        scalaBinaryVersion.value match {
          case "2.12" => "1.13.0"
          case _      => "2.0.7"
        }
      },
      addSbtPlugin("ch.epfl.scala"      % "sbt-scalafix"     % V.scalafixVersion),
      addSbtPlugin("org.portable-scala" % "sbt-crossproject" % "1.4.0"),
      addSbtPlugin("com.github.sbt"     % "sbt2-compat"      % "0.2.0"),
      // Reads `Clue.schemaDirs` out of the scalafix configuration file. Both sbt 1.x and sbt 2.x
      // already ship this jar, so depend on it as `Provided` and do not bundle a second copy.
      libraryDependencies += "com.typesafe" % "config" % "1.4.9" % Provided,
      // sbt-platform-deps supplies is only needed for sbt 1
      libraryDependencies ++= {
        if (scalaBinaryVersion.value == "2.12")
          Seq(
            Defaults.sbtPluginExtra(
              "org.portable-scala" % "sbt-platform-deps" % "1.0.2",
              (pluginCrossBuild / sbtBinaryVersion).value,
              (pluginCrossBuild / scalaBinaryVersion).value
            )
          )
        else Nil
      },
      buildInfoPackage                     := "clue.sbt",
      buildInfoKeys                        := Seq[BuildInfoKey](
        version,
        organization,
        "rulesModule" -> (genRules / moduleName).value,
        "coreModule"  -> (core.jvm / moduleName).value
      ),
      buildInfoOptions += BuildInfoOption.PackagePrivate,
      Test / test                          :=
        scripted.toTask("").value,
      scripted                             := scripted
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
      scriptedBufferLog                    := false,
      tlVersionIntroduced                  := Map(
        "3" -> "0.58.1"
      )
    )
