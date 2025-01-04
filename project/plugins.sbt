addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.10.4")
addSbtPlugin("org.scalameta"  % "sbt-scalafmt"        % "2.5.2")
addSbtPlugin("org.typelevel"  % "sbt-tpolecat"        % "0.5.2")
addSbtPlugin("ch.epfl.scala"  % "sbt-scalafix"        % "0.13.0")
resolvers += Resolver.sonatypeRepo("snapshots")
dependencyOverrides += "ch.epfl.scala"       % "scalafix-interfaces" % "0.13.0+103-6eeb97bf-SNAPSHOT"
addSbtPlugin("nl.gn0s1s"      % "sbt-dotenv"  % "3.1.0")
addSbtPlugin("com.github.sbt" % "sbt-release" % "1.4.0")
