package lila.fishnet

import cats.effect.*
import cats.effect.kernel.Resource
import cats.effect.{ IO, IOApp }
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import lila.fishnet.http.*

object App extends IOApp.Simple:

  given Logger[IO] = Slf4jLogger.getLogger[IO]

  override def run: IO[Unit] = app.useForever

  def app: Resource[IO, Unit] =
    for
      config <- Resource.eval(Config.load)
      _      <- Resource.eval(KamonInitiator.apply.init(config.kamon))
      _      <- FishnetApp(config).run()
    yield ()

class FishnetApp(config: AppConfig)(using Logger[IO]):
  def run(): Resource[IO, Unit] =
    for
      res <- AppResources.instance(config.redis)
      _   <- Resource.eval(Logger[IO].info(s"Starting lila-fishnet with config: $config"))
      lilaClient = LilaClient(res.redisPubsub)
      monitor    = Monitor.apply
      executor <- Resource.eval(Executor.instance(lilaClient, monitor))
      workListenerJob = RedisSubscriberJob(executor, res.redisPubsub)
      cleanJob        = CleanJob(executor)
      httpApi         = HttpApi(executor, HealthCheck())
      server <- MkHttpServer.apply.newEmber(config.server, httpApi.httpApp)
      _      <- workListenerJob.run().background
      _      <- cleanJob.run().background
      _ <- Resource.eval(Logger[IO].info(s"Starting server on ${config.server.host}:${config.server.port}"))
    yield ()
