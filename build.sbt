name := "lila-fishnet"

version := "1.0-SNAPSHOT"

maintainer := "lichess.org"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala, PlayNettyServer)
  .disablePlugins(PlayFilters, PlayAkkaHttpServer)

scalaVersion := "2.13.2"

val kamonVersion = "2.1.0"

libraryDependencies += guice
libraryDependencies += "io.lettuce"   % "lettuce-core"                 % "5.3.1.RELEASE"
libraryDependencies += "io.netty"     % "netty-transport-native-epoll" % "4.1.50.Final" classifier "linux-x86_64"
libraryDependencies += "joda-time"    % "joda-time"                    % "2.10.6"
libraryDependencies += "org.lichess" %% "scalachess"                   % "9.2.1"
libraryDependencies += "io.kamon"    %% "kamon-core"                   % kamonVersion
libraryDependencies += "io.kamon"    %% "kamon-influxdb"               % kamonVersion
libraryDependencies += "io.kamon"    %% "kamon-system-metrics"         % kamonVersion

resolvers += "lila-maven" at "https://raw.githubusercontent.com/ornicar/lila-maven/master"

scalacOptions ++= Seq(
  "-language:implicitConversions",
  "-feature",
  "-deprecation",
  "-Xfatal-warnings"
)

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false
