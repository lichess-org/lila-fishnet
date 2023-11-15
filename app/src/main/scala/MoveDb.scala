package lila.fishnet

import kamon.Kamon
import org.joda.time.DateTime
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.*

object MoveDb:

  case class Add(move: Work.Move)
  case class Acquire(clientKey: ClientKey)
  // private case class PostResult(workId: Work.Id, data: JsonApi.Request.PostMove)
  object Clean

  private class Monitor:

    val dbSize                  = Kamon.gauge("db.size").withoutTags()
    val dbQueued                = Kamon.gauge("db.queued").withoutTags()
    val dbAcquired              = Kamon.gauge("db.acquired").withoutTags()
    val lvl8AcquiredTimeRequest = Kamon.timer("move.acquired.lvl8").withoutTags()
    val lvl1FullTimeRequest     = Kamon.timer("move.full.lvl1").withoutTags()

    def success(work: Work.Move) =
      val now = System.currentTimeMillis
      if work.level == 8 then
        work.acquiredAt foreach { acquiredAt =>
          lvl8AcquiredTimeRequest.record(now - acquiredAt.getMillis, TimeUnit.MILLISECONDS)
        }
      if work.level == 1 then
        lvl1FullTimeRequest.record(now - work.createdAt.getMillis, TimeUnit.MILLISECONDS)

    def failure(work: Work, clientKey: ClientKey, e: Exception) = {
      // logger.warn(s"Received invalid move ${work.id} for ${work.game.id} by $clientKey", e)
    }

    def notFound(id: Work.Id, clientKey: ClientKey) = {
      // logger.info(s"Received unknown work $id by $clientKey")
    }

    def notAcquired(work: Work, clientKey: ClientKey) = {
      // logger.info(
      //   s"Received unacquired move ${work.id} for ${work.game.id} by $clientKey. Work current tries: ${work.tries} acquired: ${work.acquired}"
      // )
    }
