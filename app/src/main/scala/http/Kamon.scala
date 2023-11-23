package lila.fishnet

import cats.effect.IO
import kamon.Kamon

trait KamonInitiator:
  def init: IO[Unit]

object KamonInitiator:
  def apply: KamonInitiator = new KamonInitiator:
    def init: IO[Unit] = IO(Kamon.init())
