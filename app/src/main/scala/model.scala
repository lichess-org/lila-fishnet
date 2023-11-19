package lila.fishnet

import chess.format.Uci
import io.circe.Codec

trait StringValue extends Any:
  def value: String
  override def toString = value

trait IntValue extends Any:
  def value: Int
  override def toString = value.toString

case class ClientKey(value: String) derives Codec.AsObject

object Fishnet:
  case class Fishnet(apikey: ClientKey)
  case class Acquire(fishnet: Fishnet)
  case class PostMove(fishnet: Fishnet, move: MoveResult) derives Codec.AsObject
  case class MoveResult(bestmove: String) derives Codec.AsObject:
    def uci: Option[Uci] = Uci(bestmove)
