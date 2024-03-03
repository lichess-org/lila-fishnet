package lila.fishnet
package http

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.*
import org.http4s.implicits.*
import org.http4s.server.middleware.*
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.*

final class HttpApi(
    executor: Executor,
    healthCheck: HealthCheck,
    config: HttpServerConfig
)(using Logger[IO]):

  private val fishnetRoutes = FishnetRoutes(executor).routes
  private val healthRoutes  = HealthRoutes(healthCheck).routes

  private val routes: HttpRoutes[IO] = fishnetRoutes <+> healthRoutes

  type Middleware = HttpRoutes[IO] => HttpRoutes[IO]

  private val autoSlash: Middleware = AutoSlash(_)
  private val timeout: Middleware   = Timeout(60.seconds)

  private val middleware = autoSlash andThen timeout

  private def verboseLogger =
    RequestLogger.httpApp[IO](true, true) andThen
      ResponseLogger.httpApp[IO, Request[IO]](true, true)

  private val loggers =
    if config.apiLogger then verboseLogger
    else ApiErrorLogger.instance

  val httpApp: HttpApp[IO] =
    loggers(middleware(routes).orNotFound)
