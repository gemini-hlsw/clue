import sbtcrossproject.CrossPlugin.autoImport.crossProject

inThisBuild(
  Seq(
    scalacOptions += "-Ymacro-annotations",
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

val depsResourceDirs = taskKey[Seq[File]](
  "List of all resource directories from this project and dependencies, both managed and unmanaged."
)

// val depsResourceDirsTask: sbt.Def.Initialize[sbt.Task[Seq[sbt.SettingKey[Seq[java.io.File]]]]] =
val depsResourceDirsTask /*(conf: sbt.Configuration)*/
  : sbt.Def.Initialize[sbt.Task[Seq[java.io.File]]] =
  Def.taskDyn {
    val thisProjectRef0 = thisProjectRef.value
    Def.task {
      // (conf / resourceDirectories)
      (Compile / resourceDirectories)
        .all(ScopeFilter(inDependencies(thisProjectRef0)))
        .value
        .flatten
    }
  }

lazy val macros =
// crossProject(JVMPlatform, JSPlatform)
  project
    .in(file("macros"))
    .settings(
      depsResourceDirs := depsResourceDirsTask.value,
      moduleName := "clue-macro",
      libraryDependencies ++=
        Settings.Libraries.DisciplineMUnit.value ++
          Settings.Libraries.Grackle.value ++
          Settings.Libraries.Jawn.value ++
          Settings.Libraries.Monocle.value,
      scalacOptions += "-language:experimental.macros",
      scalacOptions ~= (_.filterNot(Set("-Wunused:patvars"))), // Needed for quasiquote matching.
      scalacOptions += {
        val thisProject    = thisProjectRef.value
        val projects       = buildDependencies.value.classpathTransitiveRefs(thisProject) :+ thisProject
        val schemaSettings =
          projects
            .filter(_.build.getScheme == "file")
            .map(project =>
              project.build.getSchemeSpecificPart + project.project
            ) // Only works if project name == directory
            // .map(_ + "/src/main/resources") // TODO Add test only when in test environment.
            .flatMap(base =>
              List(base + "/src/main/resources/graphql/schemas",
                   base + "/src/test/resources/graphql/schemas"
              )
            )
            .map(s => s"clue.schemaDir=$s")
            .mkString(",")
        // println(schemaSettings)
        "-Xmacro-settings:clue.defaultSchema=explore-simple, clue.cats.eq=true, clue.cats.show=true, clue.monocle.lenses=true, clue.scalajs-react.reusability=false," + schemaSettings
      }
    )
    // .dependsOn(core)
    .dependsOn(coreJVM)

// lazy val macrosJS = macros.js

// lazy val macrosJVM = macros.jvm
