package lila.fishnet

import cats.effect.IO

object Helper:

  val noopMonitor: Monitor =
    new Monitor:
      def success(work: Work.Task): IO[Unit]  = IO.unit
      def updateSize(map: AppState): IO[Unit] = IO.unit

  val noopLilaClient: LilaClient =
    new LilaClient:
      def send(move: Lila.Response): IO[Unit] = IO.unit

  val noopStateRepository: StateRepository =
    new StateRepository:
      def get: IO[AppState]               = IO.pure(AppState.empty)
      def save(state: AppState): IO[Unit] = IO.unit
