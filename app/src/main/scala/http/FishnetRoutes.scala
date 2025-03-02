package lila.fishnet
package http

import cats.*
import cats.effect.IO
import cats.syntax.all.*
import org.http4s.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import org.typelevel.log4cats.{ Logger, LoggerFactory }

final class FishnetRoutes(executor: Executor)(using LoggerFactory[IO]) extends Http4sDsl[IO]:

  given Logger[IO] = LoggerFactory[IO].getLoggerFromName("FishnetRoutes")

  private val prefixPath = "/fishnet"

  private val httpRoutes = HttpRoutes.of[IO]:

    case req @ POST -> Root / "acquire" =>
      req
        .decode[Fishnet.Acquire]: input =>
          acquire(input.fishnet.apikey)

    case req @ POST -> Root / "move" / WorkIdVar(id) =>
      req
        .decode[Fishnet.PostMove]: move =>
          executor.move(id, move.fishnet.apikey, move.move.bestmove)
            >> acquire(move.fishnet.apikey)

  private def acquire(key: ClientKey): IO[Response[IO]] =
    executor
      .acquire(key)
      .flatMap(_.fold(NoContent())(task => Ok(task.toResponse)))
      .handleErrorWith(x => Logger[IO].error(x.getMessage) *> InternalServerError(x.getMessage.nn))

  val routes: HttpRoutes[IO] = Router(prefixPath -> httpRoutes)

object WorkIdVar:
  def unapply(str: String): Option[WorkId] =
    WorkId(str).some
