addSbtPlugin("edu.gemini"     % "sbt-lucuma"        % "0.4.2")
addSbtPlugin("org.scala-js"   % "sbt-scalajs"       % "1.8.0")
addSbtPlugin("com.github.sbt" % "sbt-ci-release"    % "1.5.10")
addSbtPlugin("org.scalameta"  % "sbt-scalafmt"      % "2.4.6")
addSbtPlugin(("ch.epfl.scala" % "sbt-scalafix"      % "0.9.33").cross(CrossVersion.for3Use2_13))
addSbtPlugin("com.eed3si9n"   % "sbt-projectmatrix" % "0.9.0")
