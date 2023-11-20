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
      _      <- Resource.eval(Logger[IO].info(s"Starting lila-fishnet with config: $config"))
      res    <- AppResources.instance(config.redis)
      lilaClient = LilaClient(res.redisPubsub)
      executor <- Resource.eval(Executor.instance(lilaClient))
      job     = RedisSubscriberJob(executor, res.redisPubsub)
      httpApi = HttpApi(executor, HealthCheck())
      server <- MkHttpServer.apply.newEmber(config.server, httpApi.httpApp)
      _      <- job.run().background
      _      <- Resource.eval(Logger[IO].info(s"Starting server on ${config.server.host}:${config.server.port}"))
    yield ()
