package lila.fishnet
package http

import cats.*
import cats.effect.IO
import org.http4s.*
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import org.http4s.circe.CirceEntityEncoder.*

final class HealthRoutes(
    healthCheck: HealthCheck
) extends Http4sDsl[IO]:
  private[http] val prefixPath = "/health"

  private val httpRoutes: HttpRoutes[IO] = HttpRoutes.of[IO]:
    case GET -> Root =>
      Ok(healthCheck.status)

  val routes: HttpRoutes[IO] = Router(prefixPath -> httpRoutes)
