package lila.fishnet
package http

import cats.data.Kleisli
import cats.syntax.all.*
import cats.effect.IO
import org.http4s.internal.Logger as Http4sLogger
import org.http4s.{ HttpApp, Response }
import org.typelevel.log4cats.Logger

object ApiErrorLogger:

  val logOnError: Response[IO] => Boolean = res => !res.status.isSuccess && res.status.code != 404

  def instance(using Logger[IO]): HttpApp[IO] => HttpApp[IO] = http =>
    Kleisli: req =>
      http(req).flatTap: res =>
        logOnError(res)
          .pure[IO]
          .ifM(
            Http4sLogger.logMessage(req)(true, true)(Logger[IO].warn) >>
              Http4sLogger.logMessage(res)(true, true)(Logger[IO].warn),
            IO.unit
          )
