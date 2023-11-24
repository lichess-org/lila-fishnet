package lila.fishnet

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all.*
import com.comcast.ip4s.{ Host, Port }
import com.dimafeng.testcontainers.GenericContainer
import org.http4s.ember.client.EmberClientBuilder
import org.testcontainers.containers.wait.strategy.Wait
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger
import org.http4s.circe.CirceEntityDecoder.*
import weaver.*
import lila.fishnet.http.HealthCheck.AppStatus

object IntegrationTest extends SimpleIOSuite:

  given Logger[IO] = NoOpLogger[IO]
  private def resource: Resource[IO, Unit] =
    for
      redis         <- RedisContainer.startRedis
      defaultConfig <- Resource.eval(AppConfig.load)
      config = defaultConfig.copy(redis = redis)
      res <- AppResources.instance(config.redis)
      _   <- FishnetApp(res, config).run()
    yield ()

  test("health check should return healthy"):
    (resource >> client)
      .use(
        _.expect[AppStatus]("http://localhost:9665/health")
          .map(expect.same(_, AppStatus(true)))
      )

  private def client = EmberClientBuilder.default[IO].build

object RedisContainer:

  private val REDIS_PORT = 6379
  private val redisContainer =
    val start = IO(
      GenericContainer(
        "redis:6-alpine",
        exposedPorts = Seq(REDIS_PORT),
        waitStrategy = Wait.forListeningPort()
      )
    )
      .flatTap(cont => IO(cont.start()))
    Resource.make(start)(cont => IO(cont.stop()))

  def parseConfig(redis: GenericContainer): RedisConfig =
    RedisConfig(Host.fromString(redis.host).get, Port.fromInt(redis.mappedPort(REDIS_PORT)).get)

  def startRedis: Resource[IO, RedisConfig] =
    redisContainer.map(parseConfig)
