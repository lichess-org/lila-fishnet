package lila.fishnet

import cats.effect.{ IO, IOApp }
import cats.effect.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import lila.fishnet.http.*

object App extends IOApp.Simple:

  given Logger[IO] = Slf4jLogger.getLogger[IO]

  override def run: IO[Unit] = Config
    .load
    .flatMap: cfg =>
      Logger[IO].info(s"Starting lila-fishnet with config: $cfg") *>
      AppResources.instance(cfg.redis)
        .evalMap: res =>
          given lilaClient: LilaClient = LilaClient(res.redisPubsub)
          Executor.apply.map: ec =>
            HttpApi(ec, HealthCheck()).httpApp
        .flatMap: app =>
          MkHttpServer.apply.newEmber(cfg.server, app)
        .useForever
