package lila.fishnet
package http

import cats.*
import cats.effect.IO
import cats.syntax.all.*
import org.http4s.*
import org.http4s.Credentials.Token
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Authorization
import org.http4s.server.Router
import org.typelevel.log4cats.{ Logger, LoggerFactory }

final class FishnetRoutes(executor: Executor)(using LoggerFactory[IO]) extends Http4sDsl[IO]:

  given Logger[IO] = LoggerFactory[IO].getLoggerFromName("FishnetRoutes")

  private val prefixPath = "/fishnet"

  private val httpRoutes = HttpRoutes.of[IO]:

    case req @ POST -> Root / "acquire" =>
      extractClientKey(req)
        .flatMap:
          _.fold(BadRequest("Invalid request"))(acquire)

    case req @ POST -> Root / "move" / WorkIdVar(id) =>
      extractClientKey(req)
        .flatMap:
          _.fold(BadRequest("Invalid request")) { key =>
            req
              .decode[Fishnet.PostMove]: move =>
                executor.move(id, key, move.move.bestmove)
                  >> acquire(key)
          }

  private def extractClientKey(req: Request[IO]): IO[Option[ClientKey]] =
    req.headers
      .get[Authorization]
      .fold(
        Logger[IO].warn(s"Client doesn't provide apikey: ${req.headers.headers.mkString(";")}").as(None)
      ) { auth =>
        auth.credentials match
          case Token(authScheme, token) if authScheme == AuthScheme.Bearer =>
            ClientKey(token).some.pure[IO]
          case _ =>
            Logger[IO].warn(s"Client doesn't provide valid bearer token: ${auth.toString}").as(None)
      }

  private def acquire(key: ClientKey): IO[Response[IO]] =
    executor
      .acquire(key)
      .flatMap(_.fold(NoContent())(task => Ok(task.toResponse)))
      .handleErrorWith(x => Logger[IO].error(x.getMessage) *> InternalServerError(x.getMessage.nn))

  val routes: HttpRoutes[IO] = Router(prefixPath -> httpRoutes)

object WorkIdVar:
  def unapply(str: String): Option[WorkId] =
    WorkId(str).some
