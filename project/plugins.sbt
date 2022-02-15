resolvers += "sonatype-snapshots".at("https://s01.oss.sonatype.org/content/repositories/snapshots")
addSbtPlugin("edu.gemini"     % "sbt-lucuma-lib"    % "0.6.0-14-7f04804-SNAPSHOT")
addSbtPlugin(("ch.epfl.scala" % "sbt-scalafix"      % "0.9.33").cross(CrossVersion.for3Use2_13))
addSbtPlugin("com.eed3si9n"   % "sbt-projectmatrix" % "0.9.0")
