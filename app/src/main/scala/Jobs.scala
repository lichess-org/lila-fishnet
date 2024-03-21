package lila.fishnet

import cats.effect.IO
import cats.effect.kernel.Resource
import io.chrisdavenport.rediculous.RedisPubSub
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.*

import Lila.Request

trait RedisSubscriberJob:
  def run(): Resource[IO, Unit]

object RedisSubscriberJob:
  def apply(executor: Executor, pubsub: RedisPubSub[IO])(using Logger[IO]): RedisSubscriberJob = new:
    def run(): Resource[IO, Unit] =
      (Logger[IO].info("Subscribing to fishnet-out") *>
        pubsub.subscribe(
          "fishnet-out",
          msg =>
            Lila
              .readMoveReq(msg.message)
              .match
                case Some(request) => executor.add(request)
                case None          => Logger[IO].warn(s"Failed to parse message from lila: $msg")
              >> Logger[IO].debug(s"Received message: $msg")
        ) *> pubsub.runMessages).background.map(_ => ())

trait WorkCleaningJob:
  def run(): Resource[IO, Unit]

object WorkCleaningJob:
  def apply(executor: Executor)(using Logger[IO]): WorkCleaningJob = new:
    def run(): Resource[IO, Unit] =
      (Logger[IO].info("Start cleaning job") *>
        IO.sleep(5.seconds) *>
        (IO.realTimeInstant.flatMap(now => executor.clean(now.minusSeconds(3))) *>
          IO.sleep(3.seconds)).foreverM).background.map(_ => ())
