package lila.fishnet

import cats.effect.{ IO, Ref, Resource }
import cats.syntax.all.*
import com.comcast.ip4s.*
import com.dimafeng.testcontainers.GenericContainer
import io.chrisdavenport.rediculous.RedisPubSub
import io.circe.Json
import lila.fishnet.Fishnet.*
import lila.fishnet.http.HealthCheck.AppStatus
import org.http4s.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.implicits.*
import org.testcontainers.containers.wait.strategy.Wait
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger
import scala.concurrent.duration.*
import weaver.*

object IntegrationTest extends IOSuite:

  given Logger[IO] = NoOpLogger[IO]

  override type Res = AppResources
  // start our server
  override def sharedResource: Resource[IO, Res] =
    for
      redis <- RedisContainer.startRedis
      config = testAppConfig(redis = redis)
      res <- AppResources.instance(config.redis)
      _   <- FishnetApp(res, config).run()
    yield res

  def testAppConfig(redis: RedisConfig) = AppConfig(
    server = HttpServerConfig(ip"0.0.0.0", port"9999", apiLogger = false),
    redis = redis,
    kamon = KamonConfig(enabled = false),
    executor = ExecutorConfig(maxSize = 300)
  )

  test("health check should return healthy"):
    client
      .use(
        _.expect[AppStatus]("http://localhost:9999/health")
          .map(expect.same(_, AppStatus(true)))
      )

  test("let's play a game"): res =>
    val fishnet               = Fishnet("2.7.2", ClientKey("secret-key"))
    val fishnetAcquireRequest = Acquire(fishnet)
    val bestMoves             = List("e7e6", "d7d5", "d8d6")
    val postMoves             = bestMoves.map(m => PostMove(fishnet, Move(BestMove(m))))

    val gameId = "CPzkP0tq"
    val lilaRequests =
      List(
        "CPzkP0tq;1;;;;d2d4",
        "CPzkP0tq;1;;;;d2d4 e7e6 h2h4",
        "CPzkP0tq;1;;;;d2d4 e7e6 h2h4 d7d5 e2e3",
        "CPzkP0tq;1;;;;d2d4 e7e6 h2h4 d7d5 e2e3 d8d6 f1d3"
      )

    val expectedMoves = List(
      s"$gameId d2d4 e7e6",
      s"$gameId d2d4e7e6h2h4 d7d5",
      s"$gameId e7e6h2h4d7d5e2e3 d8d6"
    )

    def simulateFishnetClient(client: Client[IO]) =
      client
        .expect[Json](acquireRequest(fishnetAcquireRequest))
        .map(toWorkId)
        .flatMap: workId =>
          postMoves.foldM(workId): (workId, move) =>
            client.expect[Json](bestMoveRequest(workId, move)).map(toWorkId)

    def toWorkId(json: Json) =
      WorkId(json.hcursor.downField("work").downField("id").as[String].toOption.get)

    // sleep to make sure that moves are in order
    def scenario(client: Client[IO]) =
      lilaRequests.traverse_(sendWorkRequest(res, _) >> IO.sleep(100.millis)) >> simulateFishnetClient(client)

    val x = for
      client <- client
      ref    <- Ref.of[IO, List[String]](Nil).toResource
      _      <- RedisFishnetInListener(res.redisPubsub, ref).background
      _      <- scenario(client).toResource
      x      <- ref.get.toResource
    yield x
    x.use(x => IO.pure(expect.same(x, expectedMoves)))

  def acquireRequest(acquire: Acquire) = Request[IO](
    method = Method.POST,
    uri = uri"http://localhost:9999/fishnet/acquire"
  ).withEntity(acquire)

  def bestMoveRequest(workId: WorkId, move: PostMove) = Request[IO](
    method = Method.POST,
    uri = uri"http://localhost:9999/fishnet/move" / workId.value
  ).withEntity(move)

  private def sendWorkRequest(res: AppResources, work: String): IO[Unit] =
    res.redisPubsub.publish("fishnet-out", work).void

  private def client = EmberClientBuilder.default[IO].build

object RedisFishnetInListener:
  def apply(pubsub: RedisPubSub[IO], ref: Ref[IO, List[String]]): IO[Unit] =
    pubsub.subscribe(
      "fishnet-in",
      msg => ref.update(_ :+ msg.message)
    ) *> pubsub.runMessages

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
