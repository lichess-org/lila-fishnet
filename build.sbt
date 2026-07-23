import Dependencies.*
import org.typelevel.scalacoptions.ScalacOptions

scalaVersion      := "3.8.4"
versionScheme     := Some("early-semver")
run / fork        := true
semanticdbEnabled := true // for scalafix

Universal / target := baseDirectory.value / "target" / "universal"

val dockerSettings = Seq(
  dockerBaseImage       := "eclipse-temurin:25-jdk-noble",
  dockerUpdateLatest    := true,
  dockerBuildxPlatforms := Seq("linux/amd64", "linux/arm64")
)

lazy val app = project
  .in(file("app"))
  .settings(
    name         := "lila-fishnet",
    organization := "org.lichess",
    tpolecatScalacOptions ++= Set(
      ScalacOptions.other("-rewrite"),
      ScalacOptions.other("-indent"),
      ScalacOptions.explain,
      ScalacOptions.release("21"),
      ScalacOptions.other("-Wall")
    ),
    resolvers ++= Seq(lilaMaven, jitpack),
    libraryDependencies ++= Seq(
      catsCore,
      catsEffect,
      chess,
      circeCore,
      cirisCore,
      cirisHtt4s,
      fs2,
      http4sCirce,
      http4sDsl,
      http4sServer,
      otel4sSdk,
      otel4sPrometheusExporter,
      otel4sInstrumentationMetrics,
      otel4sHttp4sCore,
      otel4sHttp4sMetrics,
      otel4sSdkMetrics,
      log4Cats,
      logback % Runtime,
      redis,
      circeLiteral,
      chessTestKit,
      weaver,
      weaverScalaCheck,
      testContainers,
      http4sClient,
      catsEffectTestKit
    ),
    dockerSettings,
    Docker / packageName      := "lichess-org/lila-fishnet",
    Docker / maintainer       := "lichess.org",
    Docker / dockerRepository := Some("ghcr.io"),
    buildInfoKeys             := Seq[BuildInfoKey](
      version,
      BuildInfoKey.map(git.gitHeadCommit) { case (k, v) => k -> v.getOrElse("unknown").take(7) }
    ),
    buildInfoPackage := "lila.fishnet"
  )
  .enablePlugins(JavaAppPackaging, DockerPlugin, BuildInfoPlugin)

lazy val root = rootProject.autoAggregate

Global / excludeLintKeys ++= Set(
  git.gitDescribedVersion,
  git.gitUncommittedChanges,
  com.typesafe.sbt.packager.Keys.executableScriptName,
  com.typesafe.sbt.packager.Keys.daemonStdoutLogFile,
  com.typesafe.sbt.packager.Keys.rpmScriptsDirectory,
  Keys.sourceDirectory,
  Keys.name
)

addCommandAlias("prepare", "scalafixAll; scalafmtAll")
addCommandAlias(
  "check",
  "; scalafixAll --check ; scalafmtCheckAll"
)
