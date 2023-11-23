package lila.fishnet

import cats.effect.IO
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kamon.Kamon
import kamon.metric.Timer
import org.typelevel.log4cats.Logger
import java.time.Instant

trait Monitor:
  def success(work: Work.Move): IO[Unit]
  def failure(work: Work.Move, clientKey: ClientKey, e: Exception): IO[Unit]
  def notFound(id: WorkId, clientKey: ClientKey): IO[Unit]
  def notAcquired(work: Work.Move, clientKey: ClientKey): IO[Unit]
  def updateSize(map: Map[WorkId, Work.Move]): IO[Unit]

object Monitor:

  val dbSize                         = Kamon.gauge("db.size").withoutTags()
  val dbQueued                       = Kamon.gauge("db.queued").withoutTags()
  val dbAcquired                     = Kamon.gauge("db.acquired").withoutTags()
  val lvl8AcquiredTimeRequest: Timer = Kamon.timer("move.acquired.lvl8").withoutTags()
  val lvl1FullTimeRequest            = Kamon.timer("move.full.lvl1").withoutTags()

  def apply(using Logger[IO]): Monitor =
    new Monitor:
      def success(work: Work.Move): IO[Unit] =
        IO.realTimeInstant.map: now =>
          if work.request.level == 8 then
            work.acquiredAt.foreach(at => record(lvl8AcquiredTimeRequest, at, now))
          if work.request.level == 1 then record(lvl1FullTimeRequest, work.createdAt, now)

      def failure(work: Work.Move, clientKey: ClientKey, e: Exception) =
        Logger[IO].warn(e)(s"Received invalid move ${work.id} for ${work.request.id} by $clientKey")

      def notFound(id: WorkId, clientKey: ClientKey) =
        Logger[IO].info(s"Received unknown work $id by $clientKey")

      def notAcquired(work: Work.Move, clientKey: ClientKey) =
        Logger[IO].info(
          s"Received unacquired move ${work.id} for ${work.request.id} by $clientKey. Work current tries: ${work.tries} acquired: ${work.acquired}"
        )

      def updateSize(map: Map[WorkId, Work.Move]): IO[Unit] =
        IO.delay(dbSize.update(map.size.toDouble)) *>
          IO.delay(dbQueued.update(map.count(_._2.nonAcquired).toDouble)) *>
          IO.delay(dbAcquired.update(map.count(_._2.isAcquired).toDouble)).void

  private def record(timer: Timer, start: Instant, end: Instant): Unit =
    timer.record(start.until(end, ChronoUnit.MILLIS), TimeUnit.MILLISECONDS)
    ()
