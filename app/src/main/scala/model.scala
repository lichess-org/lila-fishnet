package lila.fishnet

import chess.format.Uci

trait StringValue extends Any:
  def value: String
  override def toString = value

trait IntValue extends Any:
  def value: Int
  override def toString = value.toString

case class IpAddress(value: String) extends AnyVal with StringValue

case class ClientKey(value: String) extends AnyVal with StringValue

object Fishnet:
  case class Fishnet(apikey: ClientKey)
  case class Acquire(fishnet: Fishnet)
  case class PostMove(fishnet: Fishnet, move: MoveResult)
  case class MoveResult(bestmove: String):
    def uci: Option[Uci] = Uci(bestmove)
