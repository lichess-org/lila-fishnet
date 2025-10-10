package lila.fishnet

import cats.effect.IO
import cats.syntax.all.*
import org.typelevel.otel4s.metrics.Meter

import java.time.Instant
import java.time.temporal.ChronoUnit

trait Monitor:
  def success(work: Work.Task): IO[Unit]
  def updateSize(map: AppState): IO[Unit]

object Monitor:

  def apply(meter: Meter[IO]): IO[Monitor] =
    (
      meter.gauge[Long]("db.size").create,
      meter.gauge[Long]("db.queued").create,
      meter.gauge[Long]("db.acquired").create,
      meter.histogram[Long]("move.acquired.lvl8").withUnit("ms").create,
      meter.histogram[Long]("move.full.lvl1").withUnit("ms").create
    ).mapN { case (dbSize, dbQueued, dbAccquired, moveAccquiredLvl8, moveFullLvl1) =>
      new Monitor:
        def success(work: Work.Task): IO[Unit] =
          IO.realTimeInstant.flatMap: now =>
            if work.request.level == 8 then
              work.acquiredAt.traverse_(at => record(moveAccquiredLvl8.record(_), at, now))
            else if work.request.level == 1 then record(moveFullLvl1.record(_), work.createdAt, now)
            else IO.unit

        def updateSize(state: AppState): IO[Unit] =
          dbSize.record(state.size) *>
            dbQueued.record(state.count(_.nonAcquired)) *>
            dbAccquired.record(state.count(_.isAcquired))

        private def record(f: Long => IO[Unit], start: Instant, end: Instant): IO[Unit] =
          f(start.until(end, ChronoUnit.MILLIS))
    }
