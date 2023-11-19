package lila.fishnet

import org.http4s.*
import cats.effect.{ IO, Resource }
import fs2.io.net.Network
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import org.http4s.server.defaults.Banner
import org.typelevel.log4cats.Logger

trait MkHttpServer:
  def newEmber(cfg: HttpServerConfig, httpApp: HttpApp[IO]): Resource[IO, Server]

object MkHttpServer:

  def apply(using server: MkHttpServer): MkHttpServer = server

  given forAsyncLogger(using Logger[IO]): MkHttpServer = new:

    def newEmber(cfg: HttpServerConfig, httpApp: HttpApp[IO]): Resource[IO, Server] = EmberServerBuilder
      .default[IO]
      .withHost(cfg.host)
      .withPort(cfg.port)
      .withHttpApp(httpApp)
      .build
      .evalTap(showEmberBanner)

    private def showEmberBanner(s: Server): IO[Unit] =
      Logger[IO].info(s"\n${Banner.mkString("\n")}\nHTTP Server started at ${s.address}")
