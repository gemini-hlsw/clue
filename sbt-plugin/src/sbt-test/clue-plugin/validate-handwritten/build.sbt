ThisBuild / scalaVersion := sys.props("scala.version")

lazy val app = project
  .in(file("app"))
  .enablePlugins(CluePlugin)
