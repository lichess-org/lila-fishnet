package lila.fishnet

import cats.effect.IO
import cats.syntax.all.*
import kamon.Kamon
import kamon.metric.Timer

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

trait Monitor:
  def success(work: Work.Task): IO[Unit]
  def updateSize(map: AppState): IO[Unit]

object Monitor:

  val dbSize                  = Kamon.gauge("db.size").withoutTags()
  val dbQueued                = Kamon.gauge("db.queued").withoutTags()
  val dbAcquired              = Kamon.gauge("db.acquired").withoutTags()
  val lvl8AcquiredTimeRequest = Kamon.timer("move.acquired.lvl8").withoutTags()
  val lvl1FullTimeRequest     = Kamon.timer("move.full.lvl1").withoutTags()

  def apply(): IO[Monitor] =
    (
      IO.blocking(Kamon.gauge("db.size").withoutTags()),
      IO.blocking(Kamon.gauge("db.queued").withoutTags()),
      IO.blocking(Kamon.gauge("db.acquired").withoutTags()),
      IO.blocking(Kamon.timer("move.acquired.lvl8").withoutTags()),
      IO.blocking(Kamon.timer("move.full.lvl1").withoutTags())
    ).parMapN: (dbSize, dbQueued, dbAcquired, lvl8AcquiredTimeRequest, lvl1FullTimeRequest) =>
      new Monitor:
        def success(work: Work.Task): IO[Unit] =
          IO.realTimeInstant.map: now =>
            if work.request.level == 8 then
              work.acquiredAt.foreach(at => record(lvl8AcquiredTimeRequest, at, now))
            else if work.request.level == 1 then record(lvl1FullTimeRequest, work.createdAt, now)

        def updateSize(state: AppState): IO[Unit] =
          IO(dbSize.update(state.size.toDouble)) *>
            IO(dbQueued.update(state.count(_.nonAcquired).toDouble)) *>
            IO(dbAcquired.update(state.count(_.isAcquired).toDouble)).void

        private def record(timer: Timer, start: Instant, end: Instant): Unit =
          val _ = timer.record(start.until(end, ChronoUnit.MILLIS), TimeUnit.MILLISECONDS)
