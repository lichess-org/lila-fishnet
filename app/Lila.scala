package lila.fishnet

import chess.format.{ FEN, Uci }
import io.lettuce.core._
import io.lettuce.core.pubsub._
import org.joda.time.DateTime
import play.api.Configuration
import play.api.Logger

final class Lila(
    moveDb: MoveDb,
    config: Configuration
) {

  private val logger = Logger(getClass)
  private val redis  = RedisClient create config.get[String]("redis.uri")

  def pubsub(chanIn: String, chanOut: String): Lila.Move => Unit = {

    val connIn  = redis.connectPubSub()
    val connOut = redis.connectPubSub()

    def send(move: Lila.Move): Unit = connIn.async.publish(chanIn, move.write)

    connOut.addListener(new RedisPubSubAdapter[String, String] {
      override def message(chan: String, msg: String): Unit =
        Lila.readMoveReq(msg) match {
          case None      => logger warn s"LilaOut invalid move $msg"
          case Some(req) => moveDb add req
        }
    })

    connOut.async.subscribe(chanOut) thenRun { () => connIn.async.publish(chanIn, "start") }

    send
  }
}

object Lila {

  case class Move(game: Work.Game, uci: Uci) {
    def sign  = game.moves.takeRight(20).replace(" ", "")
    def write = s"${game.id} $sign ${uci.uci}"
  }

  def readMoveReq(msg: String): Option[Work.Move] =
    msg.split(";", 6) match {
      case Array(gameId, levelS, clockS, variantS, initialFenS, moves) =>
        for {
          level <- levelS.toIntOption
          variant    = chess.variant.Variant.orDefault(variantS)
          initialFen = if (initialFenS.isEmpty) None else Some(FEN(initialFenS))
          clock      = readClock(clockS)
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

  def readClock(s: String) =
    s split ' ' match {
      case Array(ws, bs, incs) =>
        for {
          wtime <- ws.toIntOption
          btime <- bs.toIntOption
          inc   <- incs.toIntOption
        } yield Work.Clock(wtime, btime, inc)
      case _ => None
    }
}
