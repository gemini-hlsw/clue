lazy val app = project
  .in(file("app"))
  .enablePlugins(CluePlugin)
  .settings(
    scalaVersion := sys.props("scala.version")
  )
