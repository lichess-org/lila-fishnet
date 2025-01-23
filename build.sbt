import Dependencies.*
import org.typelevel.scalacoptions.ScalacOptions

inThisBuild(
  Seq(
    scalaVersion  := "3.6.3",
    versionScheme := Some("early-semver"),
    run / fork    := true,
    run / javaOptions += "-Dconfig.override_with_env_vars=true",
    semanticdbEnabled  := true, // for scalafix
    dockerBaseImage    := "openjdk:21",
    dockerUpdateLatest := true
  )
)

lazy val app = project
  .in(file("app"))
  .settings(
    name         := "lila-fishnet",
    organization := "org.lichess",
    tpolecatScalacOptions ++= Set(
      ScalacOptions.sourceFuture,
      ScalacOptions.other("-rewrite"),
      ScalacOptions.other("-indent"),
      ScalacOptions.explain,
      ScalacOptions.release("21"),
      ScalacOptions.other("-Wsafe-init") // fix in: https://github.com/typelevel/scalac-options/pull/136
    ),
    resolvers ++= Seq(Dependencies.lilaMaven),
    libraryDependencies ++= Seq(
      catsCore,
      catsEffect,
      chess,
      circeCore,
      cirisCore,
      cirisHtt4s,
      http4sCirce,
      http4sDsl,
      http4sServer,
      kamonCore,
      kamonInflux,
      kamonSystemMetrics,
      log4Cats,
      logback,
      redis,
      circeLiteral,
      chessTestKit,
      weaver,
      weaverScalaCheck,
      testContainers,
      http4sClient,
      catsEffectTestKit
    ),
    Docker / packageName      := "lichess-org/lila-fishnet",
    Docker / maintainer       := "lichess.org",
    Docker / dockerRepository := Some("ghcr.io")
  )
  .enablePlugins(JavaAppPackaging, DockerPlugin)

lazy val root = project
  .in(file("."))
  .aggregate(app)

addCommandAlias("prepare", "scalafixAll; scalafmtAll")
addCommandAlias(
  "check",
  "; scalafixAll --check ; scalafmtCheckAll"
)
