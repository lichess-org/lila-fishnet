import Dependencies.*

inThisBuild(
  Seq(
    scalaVersion  := "3.3.1",
    versionScheme := Some("early-semver"),
    version       := "3.0",
    run / fork    := true
  )
)

lazy val app = project
  .in(file("app"))
  .settings(
    name := "lila-fishnet",
    scalacOptions -= "-Xfatal-warnings",
    scalacOptions ++= Seq("-source:future", "-rewrite", "-indent", "-explain", "-Wunused:all", "-release:21"),
    resolvers ++= Seq(Dependencies.lilaMaven),
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
      kamonCore,
      kamonInflux,
      kamonSystemMetrics,
      log4Cats,
      logback,
      redis,
      circeLiteral,
      chessTestKit,
      munit,
      munitScalacheck,
      weaver,
      weaverScalaCheck,
      testContainers,
      log4CatsNoop,
      http4sClient
    )
  )
  .enablePlugins(JavaAppPackaging)

lazy val root = project
  .in(file("."))
  .aggregate(app)
