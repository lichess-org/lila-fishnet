package lila.fishnet

import chess.format.{ Uci, FEN }
import io.lettuce.core._
import org.joda.time.DateTime
import io.lettuce.core.pubsub._
import javax.inject._
import play.api.libs.json._
import play.api.Logger

@Singleton
final class Lila @Inject() (
    redisUri: RedisURI,
    moveDb: MoveDb
) {

  private val logger = Logger(getClass)
  private val redis = RedisClient create redisUri

  def pubsub[Out](chanIn: String, chanOut: String): Lila.Move => Unit = {

    val connIn = redis.connectPubSub()
    val connOut = redis.connectPubSub()

    def send(move: Lila.Move): Unit = connIn.async.publish("fishnet-in", move.write)

    connOut.async.subscribe("fishnet-out")

    connOut.addListener(new RedisPubSubAdapter[String, String] {
      override def message(chan: String, msg: String): Unit =
        Lila.readMoveReq(msg) match {
          case None => logger warn s"LilaOut invalid move $msg"
          case Some(req) => moveDb add req
        }
    })

    send
  }
}

object Lila {

  import Util.parseIntOption

  case class Move(gameId: String, uci: Uci) {
    def write = s"${gameId} ${uci}"
  }

  def readMoveReq(msg: String): Option[Work.Move] = msg.split("|", 6) match {
    case Array(gameId, levelS, clockS, variantS, initialFenS, moves) => for {
      level <- parseIntOption(levelS)
      variant <- parseIntOption(variantS) flatMap chess.variant.Variant.apply
      initialFen = if (initialFenS.isEmpty) None else Some(FEN(initialFenS))
      clock = readClock(clockS)
    } yield Work.Move(
      _id = Work.makeId,
      game = Work.Game(
        id = gameId,
        initialFen = initialFen,
        variant = variant,
        moves = moves
      ),
      level = level,
      clock = clock,
      tries = 0,
      lastTryByKey = None,
      acquired = None,
      createdAt = DateTime.now
    )
    case _ => None
  }

  def readClock(s: String) = s split ' ' match {
    case Array(ws, bs, incs) => for {
      wtime <- parseIntOption(ws)
      btime <- parseIntOption(bs)
      inc <- parseIntOption(incs)
    } yield Work.Clock(wtime, btime, inc)
    case _ => None
  }
}
