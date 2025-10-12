package lila.fishnet

import cats.effect.{ IO, Resource }
import io.chrisdavenport.rediculous.RedisPubSub
import org.typelevel.log4cats.{ Logger, LoggerFactory }

import Lila.Request

trait RedisSubscriberJob:
  def run(): Resource[IO, IO[Unit]]

object RedisSubscriberJob:
  def apply(executor: Executor, pubsub: RedisPubSub[IO])(using
      LoggerFactory[IO]
  ): RedisSubscriberJob = new:
    given Logger[IO] = LoggerFactory[IO].getLoggerFromName("RedisSubscriberJob")

    override def run(): Resource[IO, IO[Unit]] =
      (Logger[IO].info("Subscribing to fishnet-out") *>
        pubsub.subscribe(
          "fishnet-out",
          msg =>
            Lila
              .readMoveReq(msg.message)
              .fold(Logger[IO].warn(s"Failed to parse message from lila: ${msg.message}"))(executor.add)
              *> Logger[IO].debug(s"Received message: ${msg.message}")
        ) *> pubsub.runMessages).background.map(_.void)
