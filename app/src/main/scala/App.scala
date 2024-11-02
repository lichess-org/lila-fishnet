package lila.fishnet

import cats.effect.{ IO, IOApp, Resource }
import lila.fishnet.http.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object App extends IOApp.Simple:

  given Logger[IO] = Slf4jLogger.getLogger[IO]

  override def run: IO[Unit] = app.useForever

  def app: Resource[IO, Unit] =
    for
      config <- AppConfig.load().toResource
      _      <- Logger[IO].info(s"Starting lila-fishnet with config: $config").toResource
      _      <- KamonInitiator().init(config.kamon).toResource
      res    <- AppResources.instance(config.redis)
      _      <- FishnetApp(res, config).run()
    yield ()

class FishnetApp(res: AppResources, config: AppConfig)(using Logger[IO]):
  def run(): Resource[IO, Unit] =
    for
      executor <- createExecutor
      httpRoutes = HttpApi(executor, HealthCheck(), config.server).routes
      server <- MkHttpServer().newEmber(config.server, httpRoutes.orNotFound)
      _      <- RedisSubscriberJob(executor, res.redisPubsub).run()
      _      <- WorkCleaningJob(executor).run()
      _      <- Logger[IO].info(s"Starting server on ${config.server.host}:${config.server.port}").toResource
    yield ()

  private def createExecutor: Resource[IO, Executor] =
    val lilaClient = LilaClient(res.redisPubsub)
    val repository = StateRepository.instance(config.repository.path)
    Monitor().toResource.flatMap(Executor.instance(lilaClient, repository, _, config.executor))
