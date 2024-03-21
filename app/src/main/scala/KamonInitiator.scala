package lila.fishnet

import cats.effect.IO
import cats.syntax.all.*
import kamon.Kamon

trait KamonInitiator:
  def init(config: KamonConfig): IO[Unit]

object KamonInitiator:
  def apply: KamonInitiator = new:
    def init(config: KamonConfig): IO[Unit] =
      IO(Kamon.init()).whenA(config.enabled)
