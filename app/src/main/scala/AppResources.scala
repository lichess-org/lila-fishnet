package lila.fishnet

import cats.*
import cats.effect.*
import io.chrisdavenport.rediculous.{ RedisConnection, RedisPubSub }

class AppResources private (val redisPubsub: RedisPubSub[IO])

object AppResources:

  // maxQueued: How many elements before new submissions semantically block. Tradeoff of memory to queue jobs.
  // Default 1000 is good for small servers. But can easily take 100,000.
  // workers: How many threads will process pipelined messages.
  def instance(conf: RedisConfig): Resource[IO, AppResources] =
    RedisConnection
      .queued[IO]
      .withHost(conf.host)
      .withPort(conf.port)
      .withMaxQueued(1000)
      .withWorkers(workers = 2)
      .build
      .flatMap(RedisPubSub.fromConnection(_, 4096))
      .map(AppResources(_))
