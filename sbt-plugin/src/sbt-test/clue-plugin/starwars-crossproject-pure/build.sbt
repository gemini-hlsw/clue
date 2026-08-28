lazy val app = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("app"))
  .enablePlugins(CluePlugin)
  .settings(
    scalaVersion := sys.props("scala.version"),
    libraryDependencies ++= Seq(
      "dev.optics" %% "monocle-macro" % "3.3.0"
    )
  )
