package lila.fishnet

import cats.effect.IO
import io.chrisdavenport.rediculous.RedisPubSub

trait LilaClient:
  def send(move: Lila.Response): IO[Unit]

object LilaClient:

  def apply(pubsub: RedisPubSub[IO]): LilaClient = new:
    def send(move: Lila.Response): IO[Unit] = pubsub.publish("fishnet-in", move.write).void
