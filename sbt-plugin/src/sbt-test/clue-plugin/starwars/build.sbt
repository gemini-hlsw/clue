ThisBuild / scalaVersion := sys.props("scala.version")

lazy val app = project
  .in(file("app"))
  .enablePlugins(CluePlugin)
  .settings(
    libraryDependencies ++= Seq(
      "dev.optics" %% "monocle-macro" % "3.2.0"
    )
  )
