import Dependencies.*

inThisBuild(
  Seq(
    scalaVersion  := "3.4.1",
    versionScheme := Some("early-semver"),
    version       := "3.0",
    run / fork    := true,
    run / javaOptions += "-Dconfig.override_with_env_vars=true",
    semanticdbEnabled := true // for scalafix
  )
)

lazy val app = project
  .in(file("app"))
  .settings(
    name         := "lila-fishnet",
    organization := "org.lichess",
    scalacOptions ++= Seq(
      "-source:future",
      "-rewrite",
      "-indent",
      "-explain",
      "-Wunused:all",
      "-release:21",
      "-Ysafe-init"
    ),
    resolvers ++= Seq(Dependencies.lilaMaven),
    libraryDependencies ++= Seq(
      catsCore,
      catsEffect,
      chess,
      circeCore,
      cirisCore,
      cirisHtt4s,
      fs2,
      fs2IO,
      fs2Json,
      fs2JsonCirce,
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
      log4CatsNoop,
      http4sClient,
      catsEffectTestKit,
      munit,
      munitScalaCheck,
      scalacheck
    ),
    javaAgents += kamonAgent
  )
  .enablePlugins(JavaAppPackaging, JavaAgent)

lazy val root = project
  .in(file("."))
  .aggregate(app)

addCommandAlias("prepare", "scalafixAll; scalafmtAll")
addCommandAlias(
  "check",
  "; scalafixAll --check ; scalafmtCheckAll"
)
