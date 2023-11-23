package lila.fishnet

import cats.effect.IO
import kamon.Kamon

trait KamonInitiator:
  def init(config: KamonConfig): IO[Unit]

object KamonInitiator:
  def apply: KamonInitiator = new KamonInitiator:
    def init(config: KamonConfig): IO[Unit] =
      if config.enabled then
        IO(Kamon.init())
      else
        IO.unit
