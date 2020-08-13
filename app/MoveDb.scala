package lila.fishnet

import akka.actor._
import akka.pattern.ask
import java.util.concurrent.TimeUnit
import javax.inject._
import kamon.Kamon
import org.joda.time.DateTime
import play.api.Logger
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
final class MoveDb @Inject() ()(implicit system: ActorSystem, ec: ExecutionContext) {

  import MoveDb._
  import Work.Move

  implicit private val timeout = new akka.util.Timeout(2.seconds)

  def add(move: Move) = {
    monitor.moveRequest.increment()
    actor ! Add(move)
  }

  def acquire(clientKey: ClientKey): Future[Option[Move]] = {
    actor ? Acquire(clientKey) mapTo manifest[Option[Move]]
  }

  def postResult(
      workId: Work.Id,
      data: JsonApi.Request.PostMove
  ): Future[Option[Lila.Move]] = {
    actor ? PostResult(workId, data) mapTo manifest[Option[Lila.Move]]
  }

  private val actor = system.actorOf(Props(new Actor {

    val coll = scala.collection.mutable.Map.empty[Work.Id, Move]

    val maxSize = 300

    def receive = {

      case Add(move) if !coll.exists(_._2 similar move) => coll += (move.id -> move)

      case Add(move) =>
        clearIfFull()
        coll += (move.id -> move)

      case Acquire(clientKey) =>
        sender() ! coll.values
          .foldLeft[Option[Move]](None) {
            case (found, m) if m.nonAcquired =>
              Some {
                found.fold(m) { a =>
                  if (m.canAcquire(clientKey) && m.createdAt.isBefore(a.createdAt)) m else a
                }
              }
            case (found, _) => found
          }
          .map { m =>
            val move = m assignTo clientKey
            coll += (move.id -> move)
            move
          }

      case PostResult(workId, data) =>
        coll get workId match {
          case None =>
            monitor.notFound(workId, data.clientKey)
            sender() ! None
          case Some(move) if move isAcquiredBy data.clientKey =>
            data.move.uci match {
              case Some(uci) =>
                sender() ! Some(Lila.Move(move.game.id, move.game.ply, uci))
                coll -= move.id
                monitor.success(move)
              case _ =>
                sender() ! None
                updateOrGiveUp(move.invalid)
                monitor.failure(move, data.clientKey, new Exception("Missing move"))
            }
          case Some(move) =>
            sender() ! None
            monitor.notAcquired(move, data.clientKey)
        }

      case Clean =>
        val since    = DateTime.now minusSeconds 3
        val timedOut = coll.values.filter(_ acquiredBefore since)
        if (timedOut.nonEmpty) logger.debug(s"cleaning ${timedOut.size} of ${coll.size} moves")
        timedOut.foreach { m =>
          logger.info(s"Timeout move $m")
          updateOrGiveUp(m.timeout)
        }
        monitor.dbSize.update(coll.size.toDouble)
        monitor.dbQueued.update(coll.count(_._2.nonAcquired).toDouble)
        monitor.dbAcquired.update(coll.count(_._2.isAcquired).toDouble)
    }

    def updateOrGiveUp(move: Move) =
      if (move.isOutOfTries) {
        logger.warn(s"Give up on move $move")
        coll -= move.id
      } else coll += (move.id -> move)

    def clearIfFull() =
      if (coll.size > maxSize) {
        logger.warn(s"MoveDB collection is full! maxSize=$maxSize. Dropping all now!")
        coll.clear()
      }
  }))

  system.scheduler.scheduleWithFixedDelay(5.seconds, 3.seconds) { () =>
    actor ? Clean mapTo manifest[Iterable[Move]] map { moves =>
      moves foreach { move => logger.info(s"Timeout move $move") }
    }
  }

  private val logger  = Logger(getClass)
  private val monitor = new Monitor(logger)
}

object MoveDb {

  private case class Add(move: Work.Move)
  private case class Acquire(clientKey: ClientKey)
  private case class PostResult(workId: Work.Id, data: JsonApi.Request.PostMove)
  private object Clean

  final private class Monitor(logger: Logger) {

    val moveRequest             = Kamon.counter("move.request").withoutTags()
    val dbSize                  = Kamon.gauge("db.size").withoutTags()
    val dbQueued                = Kamon.gauge("db.queued").withoutTags()
    val dbAcquired              = Kamon.gauge("db.acquired").withoutTags()
    val lvl8AcquiredTimeRequest = Kamon.timer("move.acquired.lvl8").withoutTags()
    val lvl1FullTimeRequest     = Kamon.timer("move.full.lvl1").withoutTags()

    def success(work: Work.Move) = {
      val now = Util.nowMillis
      if (work.level == 8) work.acquiredAt foreach { acquiredAt =>
        lvl8AcquiredTimeRequest.record(now - acquiredAt.getMillis, TimeUnit.MILLISECONDS)
      }
      if (work.level == 1)
        lvl1FullTimeRequest.record(now - work.createdAt.getMillis, TimeUnit.MILLISECONDS)
    }

    def failure(work: Work, clientKey: ClientKey, e: Exception) = {
      logger.warn(s"Received invalid move ${work.id} for ${work.game.id} by $clientKey", e)
    }

    def notFound(id: Work.Id, clientKey: ClientKey) = {
      logger.info(s"Received unknown work $id by $clientKey")
    }

    def notAcquired(work: Work, clientKey: ClientKey) = {
      logger.info(
        s"Received unacquired move ${work.id} for ${work.game.id} by $clientKey. Work current tries: ${work.tries} acquired: ${work.acquired}"
      )
    }
  }
}
