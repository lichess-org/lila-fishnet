package lila.fishnet

import cats.syntax.all.*
import chess.format.{ Fen, Uci }
import chess.variant.Variant
import chess.variant.Variant.LilaKey
import lila.fishnet.Work.Clock
import io.circe.Encoder

object Lila:

  case class Move(game: Work.Game, uci: Uci):
    def sign  = game.moves.takeRight(20).replace(" ", "")
    def write = s"${game.id} $sign ${uci.uci}"

  // TODO: move game's fileds => Request
  case class Request(game: Work.Game, level: Int, clock: Option[Work.Clock]) derives Encoder.AsObject

  def readMoveReq(msg: String): Option[Request] =
    msg.split(";", 6) match
      case Array(gameId, levelS, clockS, variantS, initialFenS, moves) =>
        levelS.toIntOption.map: level =>
          val variant    = chess.variant.Variant.orDefault(LilaKey(variantS))
          val initialFen = readFen(initialFenS)
          val clock      = readClock(clockS)
          Request(
            game = Work.Game(
              id = gameId,
              initialFen = initialFen,
              variant = variant,
              moves = moves,
            ),
            level = level,
            clock = clock,
          )
      case _ => None

  def readFen(str: String): Option[Fen.Epd] =
    if str.nonEmpty then Some(Fen.Epd(str)) else none

  def readClock(s: String): Option[Clock] =
    s split ' ' match
      case Array(ws, bs, incs) =>
        for
          wtime <- ws.toIntOption
          btime <- bs.toIntOption
          inc   <- incs.toIntOption
        yield Work.Clock(wtime, btime, inc)
      case _ => None
