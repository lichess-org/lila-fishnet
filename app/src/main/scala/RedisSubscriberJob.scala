package lila.fishnet

import cats.effect.std.Supervisor
import cats.effect.{ Fiber, IO, Resource }
import io.chrisdavenport.rediculous.RedisPubSub
import org.typelevel.log4cats.{ Logger, LoggerFactory }

import Lila.Request

trait RedisSubscriberJob:
  def run(): Resource[IO, Fiber[IO, Throwable, Unit]]

object RedisSubscriberJob:
  def apply(executor: Executor, pubsub: RedisPubSub[IO])(using
      LoggerFactory[IO],
      Supervisor[IO]
  ): RedisSubscriberJob = new:
    given Logger[IO] = LoggerFactory[IO].getLoggerFromName("RedisSubscriberJob")

    def runIO: IO[Unit] =
      Logger[IO].info("Subscribing to fishnet-out") *>
        pubsub.subscribe(
          "fishnet-out",
          msg =>
            Lila
              .readMoveReq(msg.message)
              .fold(Logger[IO].warn(s"Failed to parse message from lila: ${msg.message}"))(executor.add)
              *> Logger[IO].debug(s"Received message: ${msg.message}")
        ) *> pubsub.runMessages

    override def run(): Resource[IO, Fiber[IO, Throwable, Unit]] =
      summon[Supervisor[IO]].supervise(runIO).toResource
