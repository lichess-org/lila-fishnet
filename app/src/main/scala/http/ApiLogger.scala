package lila.fishnet
package http

import cats.data.Kleisli
import cats.syntax.all.*
import cats.effect.IO
import org.http4s.internal.Logger as Http4sLogger
import org.http4s.{ HttpApp, Request, Response }
import org.typelevel.log4cats.Logger

object ApiErrorLogger:

  val isResponseError: Response[IO] => Boolean = res => !res.status.isSuccess && res.status.code != 404

  private def logError(req: Request[IO], res: Response[IO])(using Logger[IO]): IO[Unit] =
    Http4sLogger.logMessage(req)(true, true)(Logger[IO].warn) >>
      Http4sLogger.logMessage(res)(true, true)(Logger[IO].warn)

  def instance(using Logger[IO]): HttpApp[IO] => HttpApp[IO] = http =>
    Kleisli: req =>
      http(req).flatTap: res =>
        logError(req, res).whenA(isResponseError(res))
