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
      config <- AppConfig.load.toResource
      _      <- Logger[IO].info(s"Starting lila-fishnet with config: $config").toResource
      _      <- KamonInitiator.apply.init(config.kamon).toResource
      res    <- AppResources.instance(config.redis)
      _      <- FishnetApp(res, config).run()
    yield ()

class FishnetApp(res: AppResources, config: AppConfig)(using Logger[IO]):
  def run(): Resource[IO, Unit] =
    for
      lilaClient <- Resource.pure(LilaClient(res.redisPubsub))
      monitor = Monitor.apply
      storage = StateStorage.instance(fs2.io.file.Path("data.json"))
      executor <- Executor.instance(lilaClient, storage, monitor, config.executor)
      httpApi = HttpApi(executor, HealthCheck(), config.server)
      server <- MkHttpServer.apply.newEmber(config.server, httpApi.httpApp)
      _      <- RedisSubscriberJob(executor, res.redisPubsub).run().background
      _      <- WorkCleaningJob(executor).run().background
      _      <- Logger[IO].info(s"Starting server on ${config.server.host}:${config.server.port}").toResource
    yield ()
