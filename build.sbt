name := "lila-fishnet"

version := "1.0-SNAPSHOT"

maintainer := "lichess.org"

lazy val root = (project in file("."))
.enablePlugins(PlayScala, PlayAkkaHttpServer)
.disablePlugins(PlayFilters, PlayNettyServer)

scalaVersion := "2.13.1"

val akkaVersion = "2.5.25"

libraryDependencies += guice
libraryDependencies += "org.reactivemongo" %% "reactivemongo" % "0.18.7"
libraryDependencies += "io.lettuce" % "lettuce-core" % "5.2.0.RELEASE"
libraryDependencies += "io.netty" % "netty-transport-native-epoll" % "4.1.42.Final" classifier "linux-x86_64"
libraryDependencies += "joda-time" % "joda-time" % "2.10.4"
libraryDependencies += "com.github.blemale" %% "scaffeine" % "3.1.0" % "compile"
libraryDependencies += "org.lichess" %% "scalachess" % "9.0.25"

resolvers += "lila-maven" at "https://raw.githubusercontent.com/ornicar/lila-maven/master"

scalacOptions ++= Seq(
  "-language:implicitConversions",
  "-feature",
  "-deprecation",
  "-Xfatal-warnings"
)

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false
