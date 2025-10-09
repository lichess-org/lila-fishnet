package lila.fishnet

import cats.effect.IO
import org.typelevel.otel4s.metrics.Meter
import cats.syntax.all.*
import java.time.Instant
import java.time.temporal.ChronoUnit

trait Monitor:
  def success(work: Work.Task): IO[Unit]
  def updateSize(map: AppState): IO[Unit]

object Monitor:

  def apply(using meter: Meter[IO]): IO[Monitor] =
    (
      meter.observableGauge[Double]("db.size").createObserver,
      meter.observableGauge[Double]("db.queued").createObserver,
      meter.observableGauge[Double]("db.acquired").createObserver,
      meter.histogram[Double]("move.acquired.lvl8").create,
      meter.histogram[Double]("move.full.lvl1").create
    ).mapN { case (dbSize, dbQueued, dbAccquired, moveAccquiredLvl8, moveFullLvl1) =>
      new Monitor:
        def success(work: Work.Task): IO[Unit] =
          IO.realTimeInstant.flatMap: now =>
            if work.request.level == 8 then
              work.acquiredAt.traverse_(at => record(moveAccquiredLvl8.record(_), at, now))
            else if work.request.level == 1 then record(moveFullLvl1.record(_), work.createdAt, now)
            else IO.unit

        def updateSize(state: AppState): IO[Unit] =
          dbSize.record(state.size.toDouble) *>
            dbQueued.record(state.count(_.nonAcquired).toDouble) *>
            dbAccquired.record(state.count(_.isAcquired).toDouble)

        def record(f: Double => IO[Unit], start: Instant, end: Instant): IO[Unit] =
          f(start.until(end, ChronoUnit.MILLIS).toDouble)
    }
