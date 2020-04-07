name := "lila-fishnet"

version := "1.0-SNAPSHOT"

maintainer := "lichess.org"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala, PlayNettyServer)
  .disablePlugins(PlayFilters, PlayAkkaHttpServer)

scalaVersion := "2.13.1"

val kamonVersion = "2.0.4"

libraryDependencies += guice
libraryDependencies += "io.lettuce"  % "lettuce-core"                 % "5.2.2.RELEASE"
libraryDependencies += "io.netty"    % "netty-transport-native-epoll" % "4.1.48.Final" classifier "linux-x86_64"
libraryDependencies += "joda-time"   % "joda-time"                    % "2.10.5"
libraryDependencies += "org.lichess" %% "scalachess"                  % "9.0.27"
libraryDependencies += "io.kamon"    %% "kamon-core"                  % kamonVersion
libraryDependencies += "io.kamon"    %% "kamon-influxdb"              % "2.0.1-LILA"
libraryDependencies += "io.kamon"    %% "kamon-system-metrics"        % "2.1.0"

resolvers += "lila-maven" at "https://raw.githubusercontent.com/ornicar/lila-maven/master"

scalacOptions ++= Seq(
  "-language:implicitConversions",
  "-feature",
  "-deprecation",
  "-Xfatal-warnings"
)

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false
