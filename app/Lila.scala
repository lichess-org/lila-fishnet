package lila.fishnet

import chess.format.{ Fen, Uci }
import org.joda.time.DateTime

object Lila:

  case class Move(game: Work.Game, uci: Uci):
    def sign  = game.moves.takeRight(20).replace(" ", "")
    def write = s"${game.id} $sign ${uci.uci}"

  def readMoveReq(msg: String): Option[Work.Move] = ???

  def readClock(s: String) =
    s split ' ' match
      case Array(ws, bs, incs) =>
        for {
          wtime <- ws.toIntOption
          btime <- bs.toIntOption
          inc   <- incs.toIntOption
        } yield Work.Clock(wtime, btime, inc)
      case _ => None
