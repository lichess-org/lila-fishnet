package lila.fishnet
package http

import cats.*
import cats.syntax.all.*
import cats.effect.IO
import org.http4s.*
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*

final class UserRoutes(executor: Executor) extends Http4sDsl[IO]:

  private[http] val prefixPath = "/fishnet"

  private val httpRoutes: HttpRoutes[IO] = HttpRoutes.of[IO]:

    case req @ POST -> Root / "acquire" =>
      req
        .decode[Fishnet.Acquire]: acquire =>
          executor
            .acquire(acquire.fishnet.apikey)
            .map(_.map(_.toResponse))
            .flatMap(_.fold(NoContent())(Ok(_)))
            .recoverWith:
              case x => InternalServerError(x.getMessage().nn)

    case req @ POST -> Root / "move" / WorkIdVar(id) =>
      req
        .decode[Fishnet.PostMove]: move =>
          executor.move(id, move)
            >> executor.acquire(move.key)
              .map(_.map(_.toResponse))
              .flatMap(_.fold(NoContent())(Ok(_)))
              .recoverWith:
                case x => InternalServerError(x.getMessage().nn)

  val routes: HttpRoutes[IO] = Router(prefixPath -> httpRoutes)

object WorkIdVar:
  def unapply(str: String): Option[WorkId] =
    WorkId(str).some
