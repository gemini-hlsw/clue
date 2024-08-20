ThisBuild / scalaVersion := sys.props("scala.version")

lazy val app = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("app"))
  .enablePlugins(CluePlugin)
  .settings(
    libraryDependencies ++= Seq(
      "dev.optics" %% "monocle-macro" % "3.3.0"
    )
  )
