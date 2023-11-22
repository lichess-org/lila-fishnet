import sbt.*

object Dependencies {

  val lilaMaven = "lila-maven" at "https://raw.githubusercontent.com/lichess-org/lila-maven/master"

  object V {
    val fs2         = "3.9.3"
    val circe       = "0.14.6"
    val http4s      = "0.23.23"
    val ciris       = "3.4.0"
    val kamon       = "2.5.11"
    val kamonHttp4s = "2.6.6"
    val chess       = "15.6.11"
    val munit       = "1.0.0-M8"
  }

  def http4s(artifact: String) = "org.http4s" %% s"http4s-$artifact" % V.http4s
  def circe(artifact: String)  = "io.circe"   %% s"circe-$artifact"  % V.circe

  val chess = "org.lichess" %% "scalachess" % V.chess

  val catsCore        = "org.typelevel" %% "cats-core"             % "2.10.0"
  val catsEffect      = "org.typelevel" %% "cats-effect"           % "3.5.0"
  val catsCollections = "org.typelevel" %% "cats-collections-core" % "0.9.8"

  val fs2   = "co.fs2" %% "fs2-core" % V.fs2
  val fs2IO = "co.fs2" %% "fs2-io"   % V.fs2

  val circeCore    = circe("core")
  val circeParser  = circe("parser")
  val circeGeneric = circe("generic")
  val circeLiteral = circe("literal") % Test

  val cirisCore    = "is.cir" %% "ciris"         % V.ciris
  val cirisHtt4s   = "is.cir" %% "ciris-http4s"  % V.ciris
  val cirisRefined = "is.cir" %% "ciris-refined" % V.ciris

  val kamonCore          = "io.kamon" %% "kamon-core"           % V.kamon
  val kamonInflux        = "io.kamon" %% "kamon-influxdb"       % V.kamon
  val kamonSystemMetrics = "io.kamon" %% "kamon-system-metrics" % V.kamon
  val kamonHttp4s        = "io.kamon" %% "kamon-http4s"         % V.kamonHttp4s

  val http4sDsl    = http4s("dsl")
  val http4sServer = http4s("ember-server")
  val http4sClient = http4s("ember-client") % Test
  val http4sCirce  = http4s("circe")

  val log4Cats = "org.typelevel" %% "log4cats-slf4j"  % "2.6.0"
  val logback  = "ch.qos.logback" % "logback-classic" % "1.4.11"

  val redis = "io.chrisdavenport" %% "rediculous" % "0.5.1"

  val chessTestKit     = "org.lichess"         %% "scalachess-test-kit"        % V.chess  % Test
  val monocleCore      = "dev.optics"          %% "monocle-core"               % "3.2.0"  % Test
  val log4CatsNoop     = "org.typelevel"       %% "log4cats-noop"              % "2.6.0"  % Test
  val testContainers   = "com.dimafeng"        %% "testcontainers-scala-redis" % "0.41.0" % Test
  val weaver           = "com.disneystreaming" %% "weaver-cats"                % "0.8.3"  % Test
  val weaverScalaCheck = "com.disneystreaming" %% "weaver-scalacheck"          % "0.8.3"  % Test
  val munit            = "org.scalameta"       %% "munit"                      % V.munit  % Test
  val munitScalacheck  = "org.scalameta"       %% "munit-scalacheck"           % V.munit  % Test

}
