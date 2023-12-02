package lila.fishnet

import cats.effect.IO
import io.chrisdavenport.rediculous.RedisPubSub

trait LilaClient:
  def send(move: Lila.Move): IO[Unit]

object LilaClient:

  def apply(pubsub: RedisPubSub[IO]): LilaClient =
    new LilaClient:
      def send(move: Lila.Move): IO[Unit] = pubsub.publish("fishnet-in", move.write).void
