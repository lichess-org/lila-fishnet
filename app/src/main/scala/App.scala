package lila.fishnet

import cats.effect.{ IO, IOApp }
import cats.effect.std.Queue
import cats.effect.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import fs2.Stream

import lila.fishnet.http.*
import io.chrisdavenport.rediculous.RedisPubSub
import lila.fishnet.Lila.Request

object App extends IOApp.Simple:

  given Logger[IO] = Slf4jLogger.getLogger[IO]

  override def run: IO[Unit] = Config
    .load
    .flatMap: cfg =>
      Logger[IO].info(s"Starting lila-fishnet with config: $cfg") *>
        AppResources.instance(cfg.redis)
          .evalMap: res =>
            given LilaClient = LilaClient(res.redisPubsub)
            Executor.apply
              .map: ec =>
                subscribe(ec, res.redisPubsub) -> HttpApi(ec, HealthCheck()).httpApp
          .flatMap: (io, app) =>
            MkHttpServer.apply.newEmber(cfg.server, app)
              .flatMap: server =>
                io.background.map(_ => server)
          .useForever

  import scala.concurrent.duration.*
  def sf: Stream[IO, Unit] =
    (Stream.eval(IO.println("hello")).flatMap(_ => Stream.eval(IO.sleep(2.second)))) >> sf

  def subscribe(executor: Executor, pubsub: RedisPubSub[IO]): IO[Unit] =
    (for
      q       <- Stream.eval(Queue.unbounded[IO, Lila.Request])
      _       <- Stream.eval(subscribe(executor, pubsub, q.offer))
      request <- Stream.fromQueueUnterminated(q)
    yield request)
      .compile
      .drain

  def subscribe(executor: Executor, pubsub: RedisPubSub[IO], cb: Request => IO[Unit]): IO[Unit] =
    Logger[IO].info("Subscribing to fishnet-out") *>
      pubsub.subscribe(
        "fishnet-out",
        msg =>
          Logger[IO].info(s"Received message: $msg") *>
            Lila.readMoveReq(msg.message).match
              case Some(request) => executor.add(request)
              case None          => Logger[IO].error(s"Failed to parse message: $msg"),
      ) *> pubsub.runMessages
