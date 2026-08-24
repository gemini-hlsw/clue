lazy val app = project
  .in(file("app"))
  .enablePlugins(CluePlugin)
  .settings(
    scalaVersion := sys.props("scala.version"),
    libraryDependencies ++= Seq(
      "dev.optics" %% "monocle-macro" % "3.3.0"
    )
  )
