package lila.fishnet

import cats.effect.IO
import io.chrisdavenport.rediculous.RedisPubSub
import org.typelevel.log4cats.Logger
import Lila.Request

import scala.concurrent.duration.*

trait RedisSubscriberJob:
  def run(): IO[Unit]

object RedisSubscriberJob:
  def apply(executor: Executor, pubsub: RedisPubSub[IO])(using Logger[IO]): RedisSubscriberJob =
    new RedisSubscriberJob:
      def run(): IO[Unit] =
        Logger[IO].info("Subscribing to fishnet-out") *>
          pubsub.subscribe(
            "fishnet-out",
            msg =>
              Lila
                .readMoveReq(msg.message)
                .match
                  case Some(request) => executor.add(request)
                  case None          => Logger[IO].warn(s"Failed to parse message: $msg")
                >> Logger[IO].debug(s"Received message: $msg")
          ) *> pubsub.runMessages

trait WorkCleaningJob:
  def run(): IO[Unit]

object WorkCleaningJob:
  def apply(executor: Executor)(using Logger[IO]): WorkCleaningJob =
    new WorkCleaningJob:
      def run(): IO[Unit] =
        Logger[IO].info("Start cleaning job") *>
          IO.sleep(5.seconds) *>
          (IO.realTimeInstant.flatMap(executor.clean) *> IO.sleep(3.seconds)).foreverM
