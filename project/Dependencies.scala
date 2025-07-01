import sbt.*

object Dependencies {

  val lilaMaven = "lila-maven".at("https://raw.githubusercontent.com/lichess-org/lila-maven/master")
  val jitpack   = "jitpack".at("https://jitpack.io")

  object V {
    val catsEffect = "3.6.1"
    val chess      = "17.8.4"
    val circe      = "0.14.14"
    val ciris      = "3.9.0"
    val fs2        = "3.12.0"
    val http4s     = "0.23.30"
    val kamon      = "2.7.7"
  }

  def http4s(artifact: String) = "org.http4s" %% s"http4s-$artifact" % V.http4s
  def circe(artifact: String)  = "io.circe"   %% s"circe-$artifact"  % V.circe

  val chess = "com.github.lichess-org.scalachess" %% "scalachess" % V.chess

  val catsCore   = "org.typelevel" %% "cats-core"   % "2.13.0"
  val catsEffect = "org.typelevel" %% "cats-effect" % V.catsEffect

  val fs2 = "co.fs2" %% "fs2-core" % V.fs2

  val circeCore    = circe("core")
  val circeLiteral = circe("literal") % Test

  val cirisCore  = "is.cir" %% "ciris"        % V.ciris
  val cirisHtt4s = "is.cir" %% "ciris-http4s" % V.ciris

  val kamonCore          = "io.kamon" %% "kamon-core"           % V.kamon
  val kamonInflux        = "io.kamon" %% "kamon-influxdb"       % V.kamon
  val kamonSystemMetrics = "io.kamon" %% "kamon-system-metrics" % V.kamon

  val http4sDsl    = http4s("dsl")
  val http4sServer = http4s("ember-server")
  val http4sClient = http4s("ember-client") % Test
  val http4sCirce  = http4s("circe")

  val log4Cats = "org.typelevel" %% "log4cats-slf4j"  % "2.7.0"
  val logback  = "ch.qos.logback" % "logback-classic" % "1.5.18"

  val redis = "io.chrisdavenport" %% "rediculous" % "0.5.1"

  val chessTestKit     = "com.github.lichess-org.scalachess" %% "scalachess-test-kit"       % V.chess
  val testContainers   = "com.dimafeng"                      %% "testcontainers-scala-core" % "0.43.0" % Test
  val weaver           = "org.typelevel"                     %% "weaver-cats"               % "0.9.1"  % Test
  val weaverScalaCheck = "org.typelevel"                     %% "weaver-scalacheck"         % "0.9.1"  % Test
  val catsEffectTestKit = "org.typelevel" %% "cats-effect-testkit" % V.catsEffect % Test
}
