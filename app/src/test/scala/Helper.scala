package lila.fishnet

import cats.effect.IO

object Helper:

  val noopMonitor: Monitor = new:
    def success(work: Work.Task): IO[Unit]  = IO.unit
    def updateSize(map: AppState): IO[Unit] = IO.unit

  val noopLilaClient: LilaClient = new:
    def send(move: Lila.Response): IO[Unit] = IO.unit
