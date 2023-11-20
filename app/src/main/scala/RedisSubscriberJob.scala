package lila.fishnet

import cats.effect.IO
import cats.effect.std.Queue
import fs2.Stream
import io.chrisdavenport.rediculous.RedisPubSub
import org.typelevel.log4cats.Logger
import Lila.Request

trait RedisSubscriberJob:
  def run(): IO[Unit]

object RedisSubscriberJob:
  def apply(executor: Executor, pubsub: RedisPubSub[IO])(using Logger[IO]): RedisSubscriberJob =
    new RedisSubscriberJob:
      def run(): IO[Unit] =
        requestStream.compile.drain

      def requestStream: Stream[IO, Lila.Request] =
        for
          q       <- Stream.eval(Queue.unbounded[IO, Lila.Request])
          _       <- Stream.eval(subscribe(q.offer))
          request <- Stream.fromQueueUnterminated(q)
        yield request

      def subscribe(cb: Request => IO[Unit]): IO[Unit] =
        Logger[IO].info("Subscribing to fishnet-out") *>
          pubsub.subscribe(
            "fishnet-out",
            msg =>
              Logger[IO].info(s"Received message: $msg") *>
                Lila.readMoveReq(msg.message).match
                  case Some(request) => executor.add(request)
                  case None          => Logger[IO].error(s"Failed to parse message: $msg"),
          ) *> pubsub.runMessages
