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

final class FishnetRoutes(executor: Executor) extends Http4sDsl[IO]:

  private[http] val prefixPath = "/fishnet"

  private val httpRoutes: HttpRoutes[IO] = HttpRoutes.of[IO]:

    case req @ POST -> Root / "acquire" =>
      req
        .decode[Fishnet.Acquire]: input =>
          acquire(input.fishnet.apikey)

    case req @ POST -> Root / "move" / WorkIdVar(id) =>
      req
        .decode[Fishnet.PostMove]: move =>
          move.move.bestmove
            .fold(executor.invalidate(id, move.fishnet.apikey))(executor.move(id, move.fishnet.apikey, _))
            >> acquire(move.fishnet.apikey)
      // executor.move(id, move.fishnet.apikey, move.move.bestmove)
      //   >> acquire(move.fishnet.apikey)

  def acquire(key: ClientKey): IO[Response[IO]] =
    executor
      .acquire(key)
      .map(_.map(_.toResponse))
      .flatMap(_.fold(NoContent())(Ok(_)))
      .recoverWith:
        case x => InternalServerError(x.getMessage().nn)

  val routes: HttpRoutes[IO] = Router(prefixPath -> httpRoutes)

object WorkIdVar:
  def unapply(str: String): Option[WorkId] =
    WorkId(str).some
