resolvers += "gemini-hlsw".at(
  "https://raw.githubusercontent.com/gemini-hlsw/maven-repo/master/releases"
)

addSbtPlugin("edu.gemini"    % "sbt-lucuma-lib" % "0.17-04f40af-SNAPSHOT")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix"   % "0.14.9")
addSbtPlugin("com.eed3si9n"  % "sbt-buildinfo"  % "0.13.2")
