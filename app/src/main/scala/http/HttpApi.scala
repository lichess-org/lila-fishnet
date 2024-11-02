package lila.fishnet
package http

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.*
import org.http4s.server.middleware.*
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.*

final class HttpApi(
    executor: Executor,
    healthCheck: HealthCheck,
    config: HttpServerConfig
)(using Logger[IO]):

  private def fishnetRoutes = FishnetRoutes(executor).routes
  private def healthRoutes  = HealthRoutes(healthCheck).routes

  private def middleware = ApiLogger(config.apiLogger).andThen(AutoSlash(_)).andThen(Timeout(60.seconds))

  def routes: HttpRoutes[IO] = middleware(fishnetRoutes <+> healthRoutes)
