package lila.fishnet
package http

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.*
import org.http4s.otel4s.middleware.metrics.OtelMetrics
import org.http4s.server.middleware.*
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.otel4s.metrics.MeterProvider

import scala.concurrent.duration.*

final class HttpApi(
    executor: Executor,
    healthCheck: HealthCheck,
    config: HttpServerConfig
)(using LoggerFactory[IO], MeterProvider[IO]):

  private def fishnetRoutes = FishnetRoutes(executor).routes
  private def healthRoutes  = HealthRoutes(healthCheck).routes

  private def middleware =
    OtelMetrics
      .serverMetricsOps[IO]()
      .map(org.http4s.server.middleware.Metrics[IO](_))
      .map: metrics =>
        ApiLogger(config.apiLogger).andThen(AutoSlash(_)).andThen(Timeout(60.seconds)).andThen(metrics)

  def routes: IO[HttpRoutes[IO]] = middleware.map(_(fishnetRoutes <+> healthRoutes))
