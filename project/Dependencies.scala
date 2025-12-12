import sbt.*

object Dependencies {

  val lilaMaven = "lila-maven".at("https://raw.githubusercontent.com/lichess-org/lila-maven/master")
  val jitpack   = "jitpack".at("https://jitpack.io")

  object V {
    val catsEffect   = "3.6.3"
    val chess        = "17.14.1"
    val circe        = "0.14.15"
    val ciris        = "3.11.1"
    val fs2          = "3.12.2"
    val http4s       = "0.23.33"
    val otel4s       = "0.14.0"
    val otel4sHttp4s = "0.15.0"
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

  val redis = "io.chrisdavenport" %% "rediculous" % "0.5.1"

  val http4sDsl    = http4s("dsl")
  val http4sServer = http4s("ember-server")
  val http4sClient = http4s("ember-client") % Test
  val http4sCirce  = http4s("circe")

  val log4Cats = "org.typelevel" %% "log4cats-slf4j"  % "2.7.0"
  val logback  = "ch.qos.logback" % "logback-classic" % "1.5.22"

  val otel4sCore                   = "org.typelevel" %% "otel4s-core"                    % V.otel4s
  val otel4sPrometheusExporter     = "org.typelevel" %% "otel4s-sdk-exporter-prometheus" % V.otel4s
  val otel4sSdk                    = "org.typelevel" %% "otel4s-sdk"                     % V.otel4s
  val otel4sInstrumentationMetrics = "org.typelevel" %% "otel4s-instrumentation-metrics" % V.otel4s
  val otel4sMetrics                = "org.typelevel" %% "otel4s-experimental-metrics"    % "0.8.1"

  val otel4sHttp4sCore    = "org.http4s" %% "http4s-otel4s-middleware-core"    % V.otel4sHttp4s
  val otel4sHttp4sMetrics = "org.http4s" %% "http4s-otel4s-middleware-metrics" % V.otel4sHttp4s

  val chessTestKit     = "com.github.lichess-org.scalachess" %% "scalachess-test-kit"       % V.chess
  val testContainers   = "com.dimafeng"                      %% "testcontainers-scala-core" % "0.44.0" % Test
  val weaver           = "org.typelevel"                     %% "weaver-cats"               % "0.11.2" % Test
  val weaverScalaCheck = "org.typelevel"                     %% "weaver-scalacheck"         % "0.11.2" % Test
  val catsEffectTestKit = "org.typelevel" %% "cats-effect-testkit" % V.catsEffect % Test
}
