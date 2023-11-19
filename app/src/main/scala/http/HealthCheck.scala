package lila.fishnet
package http

import io.circe.{ Codec, Decoder, Encoder }
import cats.effect.IO
import HealthCheck.*

trait HealthCheck:
  def status: IO[AppStatus]

object HealthCheck:

  case class AppStatus(status: Boolean) derives Codec.AsObject
