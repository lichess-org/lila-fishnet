package lila.fishnet

import cats.effect.IO
import cats.syntax.all.*
import ciris.*
import ciris.http4s.*
import com.comcast.ip4s.*

object AppConfig:

  def load(): IO[AppConfig] = appConfig.load[IO]

  def appConfig = (
    RedisConfig.config,
    HttpServerConfig.config,
    ExecutorConfg.config
  ).parMapN(AppConfig.apply)

case class AppConfig(
    redis: RedisConfig,
    server: HttpServerConfig,
    executor: ExecutorConfig
)

case class HttpServerConfig(
    host: Host,
    port: Port,
    apiLogger: Boolean,
    shutdownTimeout: Int,
    authMask: Ipv4Address
)

object HttpServerConfig:
  given ConfigDecoder[String, Ipv4Address] =
    ConfigDecoder.lift(value =>
      Ipv4Address.fromString(value).toRight(ConfigError(s"Invalid IPv4 subnet mask: $value"))
    )

  def host            = env("HTTP_HOST").or(prop("http.host")).as[Host].default(ip"0.0.0.0")
  def port            = env("HTTP_PORT").or(prop("http.port")).as[Port].default(port"9665")
  def logger          = env("HTTP_API_LOGGER").or(prop("http.api.logger")).as[Boolean].default(false)
  def shutdownTimeout = env("HTTP_SHUTDOWN_TIMEOUT").or(prop("http.shutdown.timeout")).as[Int].default(30)
  def authMask        = env("HTTP_AUTH_MASK")
    .or(prop("http.auth.mask"))
    .as[Ipv4Address]
    .default(ipv4"255.255.255.255")
  def config = (host, port, logger, shutdownTimeout, authMask).parMapN(HttpServerConfig.apply)

case class RedisConfig(host: Host, port: Port)

object RedisConfig:
  private def host = env("REDIS_HOST").or(prop("redis.host")).as[Host].default(ip"127.0.0.1")
  private def port = env("REDIS_PORT").or(prop("redis.port")).as[Port].default(port"6379")
  def config       = (host, port).parMapN(RedisConfig.apply)

case class ExecutorConfig(maxSize: Int)
object ExecutorConfg:
  def maxSize = env("APP_MAX_MOVE_SIZE").or(prop("app.max.move.size")).as[Int].default(300)
  def config  = maxSize.map(ExecutorConfig.apply)
