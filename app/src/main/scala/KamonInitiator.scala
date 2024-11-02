package lila.fishnet

import cats.effect.IO
import cats.syntax.all.*

trait KamonInitiator:
  def init(config: KamonConfig): IO[Unit]

object KamonInitiator:
  def apply(): KamonInitiator = new:
    def init(config: KamonConfig): IO[Unit] =
      IO.blocking(kamon.Kamon.init()).whenA(config.enabled)
