name := "lila-fishnet"

version := "2.0"

maintainer := "lichess.org"

lazy val root = Project("lila-fishnet", file("."))
  .enablePlugins(PlayScala, PlayNettyServer)
  .disablePlugins(PlayAkkaHttpServer)

scalaVersion := "2.13.8"
resourceDirectory in Compile := baseDirectory.value / "conf"

val kamonVersion = "2.5.8"

libraryDependencies += "io.lettuce"   % "lettuce-core"                 % "6.2.1.RELEASE"
libraryDependencies += "io.netty"     % "netty-transport-native-epoll" % "4.1.84.Final" classifier "linux-x86_64"
libraryDependencies += "joda-time"    % "joda-time"                    % "2.11.2"

libraryDependencies += "org.lichess" %% "scalachess"                   % "10.5.0"
libraryDependencies += "io.kamon"    %% "kamon-core"                   % kamonVersion
libraryDependencies += "io.kamon"    %% "kamon-influxdb"               % kamonVersion
libraryDependencies += "io.kamon"    %% "kamon-system-metrics"         % kamonVersion

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

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false
