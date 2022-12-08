name := "lila-fishnet"

version := "2.0"

maintainer := "lichess.org"

lazy val root = Project("lila-fishnet", file("."))
  .enablePlugins(PlayScala, PlayNettyServer)
  .disablePlugins(PlayAkkaHttpServer)

scalaVersion := "2.13.10"
Compile / resourceDirectory := baseDirectory.value / "conf"

val kamonVersion = "2.5.11"

libraryDependencies += "io.lettuce" % "lettuce-core"                 % "6.2.2.RELEASE"
libraryDependencies += "io.netty"   % "netty-transport-native-epoll" % "4.1.85.Final" classifier "linux-x86_64"
libraryDependencies += "joda-time"  % "joda-time"                    % "2.12.2"

libraryDependencies += "org.lichess" %% "scalachess"           % "10.6.3"
libraryDependencies += "io.kamon"    %% "kamon-core"           % kamonVersion
libraryDependencies += "io.kamon"    %% "kamon-influxdb"       % kamonVersion
libraryDependencies += "io.kamon"    %% "kamon-system-metrics" % kamonVersion

resolvers += "lila-maven" at "https://raw.githubusercontent.com/ornicar/lila-maven/master"

scalacOptions ++= Seq(
  "-explaintypes",
  "-feature",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-Ymacro-annotations",
  // Warnings as errors!
  // "-Xfatal-warnings",
  // Linting options
  "-unchecked",
  "-Xcheckinit",
  "-Xlint:adapted-args",
  "-Xlint:constant",
  "-Xlint:delayedinit-select",
  "-Xlint:deprecation",
  "-Xlint:inaccessible",
  "-Xlint:infer-any",
  "-Xlint:missing-interpolator",
  "-Xlint:nullary-unit",
  "-Xlint:option-implicit",
  "-Xlint:package-object-classes",
  "-Xlint:poly-implicit-overload",
  "-Xlint:private-shadow",
  "-Xlint:stars-align",
  "-Xlint:type-parameter-shadow",
  "-Wdead-code",
  "-Wextra-implicit",
  "-Wnumeric-widen",
  "-Wunused:imports",
  "-Wunused:locals",
  "-Wunused:patvars",
  "-Wunused:privates",
  "-Wunused:implicits",
  "-Wunused:params"
  /* "-Wvalue-discard" */
)

javaOptions ++= Seq("-Xms64m", "-Xmx128m")

Compile / doc / sources := Seq.empty
Compile / packageDoc / publishArtifact := false
