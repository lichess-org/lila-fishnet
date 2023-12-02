package lila.fishnet

import cats.effect.IO

object Helper:

  val noopMonitor: Monitor =
    new Monitor:
      def success(work: Work.Move): IO[Unit]                                     = IO.unit
      def failure(work: Work.Move, clientKey: ClientKey, e: Exception): IO[Unit] = IO.unit
      def notFound(id: WorkId, clientKey: ClientKey): IO[Unit]                   = IO.unit
      def notAcquired(work: Work.Move, clientKey: ClientKey): IO[Unit]           = IO.unit
      def updateSize(map: Map[WorkId, Work.Move]): IO[Unit]                      = IO.unit

  val noopLilaClient: LilaClient =
    new LilaClient:
      def send(move: Lila.Move): IO[Unit] = IO.unit
