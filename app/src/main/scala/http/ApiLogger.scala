package lila.fishnet
package http

import cats.data.{ Kleisli, OptionT }
import cats.effect.IO
import cats.syntax.all.*
import org.http4s.internal.Logger as Http4sLogger
import org.http4s.server.middleware.{ RequestLogger, ResponseLogger }
import org.http4s.{ HttpRoutes, Request, Response }
import org.typelevel.log4cats.Logger

object ApiLogger:

  private def isResponseError(res: Response[IO]): Boolean =
    !res.status.isSuccess && res.status.code != 404

  private def logError(req: Request[IO], res: Response[IO])(using Logger[IO]): IO[Unit] =
    Http4sLogger.logMessage(req)(true, true)(Logger[IO].warn) >>
      Http4sLogger.logMessage(res)(true, true)(Logger[IO].warn)

  def apply(isVerbose: Boolean)(using Logger[IO]): HttpRoutes[IO] => HttpRoutes[IO] =
    if isVerbose then verboseLogger
    else errorLogger

  private def errorLogger(using Logger[IO]): HttpRoutes[IO] => HttpRoutes[IO] = http =>
    Kleisli: req =>
      http(req).flatTap: res =>
        OptionT.liftF(logError(req, res).whenA(isResponseError(res)))

  private def verboseLogger =
    RequestLogger.httpRoutes[IO](true, true) andThen
      ResponseLogger.httpRoutes[IO, Request[IO]](true, true)
