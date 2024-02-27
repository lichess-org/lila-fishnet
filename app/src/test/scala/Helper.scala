package lila.fishnet

import cats.effect.IO

object Helper:

  val noopMonitor: Monitor =
    new Monitor:
      def success(work: Work.Move): IO[Unit]                = IO.unit
      def updateSize(map: Map[WorkId, Work.Move]): IO[Unit] = IO.unit

  val noopLilaClient: LilaClient =
    new LilaClient:
      def send(move: Lila.Move): IO[Unit] = IO.unit
