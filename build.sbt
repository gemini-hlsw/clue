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

// val depsResourceDirs = taskKey[Seq[Seq[File]]](
//   "List of all resource directories from this project and dependencies, both managed and unmanaged."
// )

// val depsResourceDirsTask: sbt.Def.Initialize[sbt.Task[Seq[sbt.SettingKey[Seq[java.io.File]]]]] =
//   Def.taskDyn {
//     val deps =
//       buildDependencies.value
//         .classpathTransitiveRefs(
//           ProjectRef((LocalRootProject / baseDirectory).value, name.value)
//         )
//     Def.task {
//       deps.map(project => (project / Compile / resourceDirectories))
//     }
//   } //.value

lazy val macros =
// crossProject(JVMPlatform, JSPlatform)
  project
    .in(file("macros"))
    .settings(
      // depsResourceDirs := depsResourceDirsTask.value.map(_.value),
      moduleName := "clue-macro",
      libraryDependencies ++=
        Settings.Libraries.DisciplineMUnit.value ++
          Settings.Libraries.Grackle.value ++
          Settings.Libraries.Jawn.value ++
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
      )),
      scalacOptions += {
        val thisProject   = thisProjectRef.value
        val projects      = buildDependencies.value.classpathTransitiveRefs(thisProject) :+ thisProject
        val macroSettings =
          projects
            .filter(_.build.getScheme == "file")
            .map(project =>
              project.build.getSchemeSpecificPart + project.project
            ) // Only works if project name == directory
            // .map(_ + "/src/main/resources") // TODO Add test only when in test environment.
            .flatMap(base => List(base + "/src/main/resources", base + "/src/test/resources"))
            .map(s => s"clue.path=$s")
            .mkString(",")
        // println(macroSettings)
        "-Xmacro-settings:" + macroSettings
      }
    )
    // .dependsOn(core)
    .dependsOn(coreJVM)

// lazy val macrosJS = macros.js

// lazy val macrosJVM = macros.jvm
