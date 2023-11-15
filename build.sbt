import Dependencies._

inThisBuild(
  Seq(
    scalaVersion  := "3.3.1",
    versionScheme := Some("early-semver"),
    name          := "lila-fishnet",
    version       := "2.0",
    run / fork    := true,
  )
)

lazy val app = project
  .in(file("app"))
  .settings(
    scalacOptions -= "-Xfatal-warnings",
    scalacOptions ++= Seq("-source:future", "-rewrite", "-indent", "-explain", "-Wunused:all"),
    resolvers ++= Seq(Dependencies.lilaMaven),
    libraryDependencies ++= Seq(
      catsCore,
      catsEffect,
      chess,
      circeCore,
      circeGeneric,
      circeParser,
      cirisCore,
      cirisHtt4s,
      fs2,
      http4sClient,
      jodaTime,
      kamonCore,
      kamonInflux,
      kamonSystemMetrics,
      log4Cats,
      monocleCore,
      weaver,
      weaverScalaCheck,
    ),
  )

lazy val root = project
  .in(file("."))
  .aggregate(app)
