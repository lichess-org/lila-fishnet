package lila.fishnet

import cats.effect.{ IO, IOApp }
import cats.effect.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import lila.fishnet.http.*

object App extends IOApp.Simple:

  given Logger[IO] = Slf4jLogger.getLogger[IO]

  given client: LilaClient = new LilaClient:
    def send(move: Lila.Move): IO[Unit] =
      IO.println(s"send $move")

  override def run: IO[Unit] = Config
    .load
    .flatMap: cfg =>
      Executor.apply.flatMap: ec =>
        val app = HttpApi(ec, HealthCheck()).httpApp
        MkHttpServer.apply.newEmber(cfg.server, app)
          .useForever
