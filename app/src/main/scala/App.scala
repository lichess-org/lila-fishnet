package lila.fishnet

import cats.effect.{ IO, IOApp, Resource }
import cats.syntax.all.*
import lila.fishnet.http.*
import org.typelevel.log4cats.slf4j.{ Slf4jFactory, Slf4jLogger }
import org.typelevel.log4cats.{ Logger, LoggerFactory }

object App extends IOApp.Simple:

  given Logger[IO]        = Slf4jLogger.getLogger[IO]
  given LoggerFactory[IO] = Slf4jFactory.create[IO]

  override def run: IO[Unit] = app.useForever

  def app: Resource[IO, Unit] =
    for
      config <- AppConfig.load().toResource
      _      <- Logger[IO].info(s"Starting lila-fishnet with config: $config").toResource
      _      <- KamonInitiator().init(config.kamon).toResource
      res    <- AppResources.instance(config.redis)
      _      <- FishnetApp(res, config).run()
    yield ()

class FishnetApp(res: AppResources, config: AppConfig)(using LoggerFactory[IO]):
  given Logger[IO] = LoggerFactory[IO].getLoggerFromName("FishnetApp")
  def run(): Resource[IO, Unit] =
    for
      executor <- createExecutor
      httpRoutes = HttpApi(executor, HealthCheck(), config.server).routes
      server <- MkHttpServer().newEmber(config.server, httpRoutes.orNotFound)
      _      <- RedisSubscriberJob(executor, res.redisPubsub).run()
      _      <- WorkCleaningJob(executor).run()
      _      <- Logger[IO].info(s"Starting server on ${config.server.host}:${config.server.port}").toResource
      _      <- Logger[IO].info(s"BuildInfo: $BuildInfo").toResource
    yield ()

  private def createExecutor: Resource[IO, Executor] =
    val lilaClient = LilaClient(res.redisPubsub)
    Monitor().toResource >>= Executor.instance(lilaClient, config.executor)
