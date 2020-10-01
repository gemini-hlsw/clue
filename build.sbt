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

// ***** START: Move to plugin *****
val depsResourceDirs = taskKey[Seq[File]](
  "List of all resource directories from this project and dependencies, both managed and unmanaged."
)
def depsResourceDirsTask(conf: ConfigKey): sbt.Def.Initialize[sbt.Task[Seq[java.io.File]]] =
  Def.taskDyn {
    val thisProjectRef0 = thisProjectRef.value
    Def.task {
      resourceDirectories
        .in(conf)
        .all(ScopeFilter(inDependencies(thisProjectRef0)))
        .value
        .flatten
    }
  }

val clueGenEq =
  settingKey[Boolean]("Include cats.Eq instances for classes generated by clue macros.")
Global / clueGenEq := false

val clueGenShow =
  settingKey[Boolean]("Include cats.Show instances for classes generated by clue macros.")
Global / clueGenShow := false

val clueGenLenses =
  settingKey[Boolean]("Include monocle.Lens instances for classes generated by clue macros.")
Global / clueGenLenses := false

val clueGenReusability =
  settingKey[Boolean](
    "Include japgolly.scalajs.react.Reusability instances for classes generated by clue macros."
  )
Global / clueGenReusability := false

val clueGeneralSettings = taskKey[Seq[String]]("General macro settings for clue.")
def clueGeneralSettingsTask(conf: ConfigKey): sbt.Def.Initialize[sbt.Task[Seq[String]]] =
  Def.task {
    List(
      s"clue.cats.eq=${clueGenEq.in(conf).value}",
      s"clue.cats.show=${clueGenShow.in(conf).value}",
      s"clue.monocle.lenses=${clueGenLenses.in(conf).value}",
      s"clue.scalajs-react.reusability=${clueGenReusability.in(conf).value}"
    )
  }

val clueSchemaDirSettings = taskKey[Seq[String]]("Schema dirs macro settings for clue.")
def clueSchemaDirSettingsTask(conf: ConfigKey): sbt.Def.Initialize[sbt.Task[Seq[String]]] =
  Def.taskDyn {
    val resourceDirs = depsResourceDirs.in(conf).value
    Def.task {
      resourceDirs.map(f => s"clue.schemaDir=${f.getAbsolutePath}/graphql/schemas")
    }
  }
// ***** END: Move to plugin *****

lazy val macros =
// crossProject(JVMPlatform, JSPlatform)
  project
    .in(file("macros"))
    .settings(
      // ***** START: Move to plugin *****
      depsResourceDirs in Compile := depsResourceDirsTask(Compile).value,
      Test / depsResourceDirs := depsResourceDirsTask(Test).value,
      clueGeneralSettings := clueGeneralSettingsTask(Compile).value,
      Test / clueGeneralSettings := clueGeneralSettingsTask(Test).value,
      clueSchemaDirSettings := clueSchemaDirSettingsTask(Compile).value,
      Test / clueSchemaDirSettings := clueSchemaDirSettingsTask(Test).value,
      scalacOptions += "-Xmacro-settings:" +
        (clueGeneralSettings.value ++ clueSchemaDirSettings.value).mkString(","),
      Test / scalacOptions += "-Xmacro-settings:" +
        ((Test / clueGeneralSettings).value ++ (Test / clueSchemaDirSettings).value).mkString(","),
      // ***** END: Move to plugin *****
      clueGenEq := true,
      clueGenEq in Test := false,
      clueGenShow := true,
      clueGenLenses := true,
      moduleName := "clue-macro",
      libraryDependencies ++=
        Settings.Libraries.DisciplineMUnit.value ++
          Settings.Libraries.Grackle.value ++
          Settings.Libraries.Jawn.value ++
          Settings.Libraries.Monocle.value,
      scalacOptions += "-language:experimental.macros",
      scalacOptions ~= (_.filterNot(Set("-Wunused:patvars"))) // Needed for quasiquote matching.
    )
    // .dependsOn(core)
    .dependsOn(coreJVM)

// lazy val macrosJS = macros.js

// lazy val macrosJVM = macros.jvm
