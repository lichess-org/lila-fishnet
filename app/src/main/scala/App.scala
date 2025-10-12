package lila.fishnet

import cats.effect.{ IO, IOApp, Resource }
import cats.syntax.all.*
import lila.fishnet.http.*
import org.http4s.HttpRoutes
import org.http4s.server.Router
import org.typelevel.log4cats.slf4j.{ Slf4jFactory, Slf4jLogger }
import org.typelevel.log4cats.{ Logger, LoggerFactory }
import org.typelevel.otel4s.experimental.metrics.*
import org.typelevel.otel4s.instrumentation.ce.IORuntimeMetrics
import org.typelevel.otel4s.metrics.{ Meter, MeterProvider }
import org.typelevel.otel4s.sdk.exporter.prometheus.*
import org.typelevel.otel4s.sdk.metrics.SdkMetrics
import org.typelevel.otel4s.sdk.metrics.SdkMetrics.AutoConfigured.Builder
import org.typelevel.otel4s.sdk.metrics.exporter.MetricExporter

object App extends IOApp.Simple:

  given Logger[IO]        = Slf4jLogger.getLogger[IO]
  given LoggerFactory[IO] = Slf4jFactory.create[IO]

  override def run: IO[Unit] =
    appR.use(_.run())

  def appR: Resource[IO, FishnetApp] =
    for
      given MetricExporter.Pull[IO] <- PrometheusMetricExporter.builder[IO].build.toResource
      otel4s                        <- SdkMetrics.autoConfigured[IO](configBuilder)
      given MeterProvider[IO] = otel4s.meterProvider
      _      <- registerRuntimeMetrics
      config <- AppConfig.load().toResource
      _      <- Logger[IO].info(s"Starting lila-fishnet with config: ${config.toString}").toResource
      res    <- AppResources.instance(config.redis)
    yield FishnetApp(res, config, MkPrometheusRoutes)

  private def registerRuntimeMetrics(using MeterProvider[IO]): Resource[IO, Unit] =
    for
      _               <- IORuntimeMetrics.register[IO](runtime.metrics, IORuntimeMetrics.Config.default)
      given Meter[IO] <- MeterProvider[IO].get("jvm.runtime").toResource
      _               <- RuntimeMetrics.register[IO]
    yield ()

  private def configBuilder(builder: Builder[IO])(using exporter: MetricExporter.Pull[IO]) =
    builder
      .addPropertiesCustomizer(_ =>
        Map(
          "otel.metrics.exporter" -> "none",
          "otel.traces.exporter"  -> "none"
        )
      )
      .addMeterProviderCustomizer((b, _) => b.registerMetricReader(exporter.metricReader))

class FishnetApp(res: AppResources, config: AppConfig, metricsRoute: HttpRoutes[IO])(using
    LoggerFactory[IO],
    MeterProvider[IO]
):
  given Logger[IO] = LoggerFactory[IO].getLogger

  def run() =
    makeResource().use:
      _ *>
        Logger[IO].error("Redis Connection is closed. FishnetApp terminated") *>
        IO.raiseError(new Exception("Redis Connection is closed. FishnetApp terminated"))

  def makeResource() =
    for
      executor   <- createExecutor.toResource
      httpRoutes <- HttpApi(executor, HealthCheck(), config.server).routes.toResource
      allRoutes = httpRoutes <+> metricsRoute
      io <- RedisSubscriberJob(executor, res.redisPubsub).run()
      _  <- WorkCleaningJob(executor).run()
      _  <- banner.toResource
      _  <- MkHttpServer().newEmber(config.server, allRoutes.orNotFound)
    yield io

  private def banner =
    Logger[IO].info(s"==============================================") *>
      Logger[IO].info(s"Starting server on ${config.server.host.toString}:${config.server.port.toString}") *>
      Logger[IO].info(s"BuildInfo: ${BuildInfo.toString}")

  private def createExecutor: IO[Executor] =
    MeterProvider[IO].get("lila.fishnet")
      >>= Monitor.apply
      >>= Executor.instance(LilaClient(res.redisPubsub), config.executor)

def MkPrometheusRoutes(using exporter: MetricExporter.Pull[IO]): HttpRoutes[IO] =
  val writerConfig     = PrometheusWriter.Config.default
  val prometheusRoutes = PrometheusHttpRoutes.routes[IO](exporter, writerConfig)
  Router("/metrics" -> prometheusRoutes)
