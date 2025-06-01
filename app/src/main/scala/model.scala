package lila.fishnet

import cats.syntax.all.*
import chess.format.{ Fen, Uci }
import chess.variant.Variant
import chess.variant.Variant.LilaKey
import io.circe.Decoder.decodeString
import io.circe.Encoder.encodeString
import io.circe.{ Codec, Decoder, Encoder }

opaque type ClientKey = String
object ClientKey:
  def apply(value: String): ClientKey         = value
  given Encoder[ClientKey]                    = encodeString
  given Decoder[ClientKey]                    = decodeString
  extension (ck: ClientKey) def value: String = ck

opaque type BestMove = Uci
object BestMove:
  def apply(value: Uci): BestMove         = value
  given Encoder[BestMove]                 = encodeString.contramap(_.uci)
  given Decoder[BestMove]                 = decodeString.emap(s => Uci(s).toRight(s"Invalid Uci: $s"))
  extension (bm: BestMove) def value: Uci = bm

opaque type WorkId = String
object WorkId:
  def apply(value: String): WorkId         = value
  given Encoder[WorkId]                    = encodeString
  given Decoder[WorkId]                    = decodeString
  extension (id: WorkId) def value: String = id

opaque type GameId = String
object GameId:
  def apply(value: String): GameId         = value
  given Encoder[GameId]                    = encodeString
  given Decoder[GameId]                    = decodeString
  extension (id: GameId) def value: String = id

object ChessCirceCodecs:
  given Encoder[Fen.Full] = encodeString.contramap(_.value)
  given Decoder[Fen.Full] = decodeString.map(Fen.Full.apply)
  given Encoder[Variant]  = encodeString.contramap(_.name)
  given Decoder[Variant]  =
    decodeString.emap: s =>
      Variant.byName(s).toRight(s"Invalid variant: $s")

object Fishnet:

  import ChessCirceCodecs.given

  case class Acquire(fishnet: Fishnet) derives Codec.AsObject
  case class Fishnet(version: String, apikey: ClientKey) derives Codec.AsObject
  case class PostMove(fishnet: Fishnet, move: Move) derives Codec.AsObject
  case class Move(bestmove: Option[BestMove]) derives Codec.AsObject

  case class Work(id: WorkId, level: Int, clock: Option[Lila.Clock], `type`: String = "move")
      derives Encoder.AsObject

  case class WorkResponse(
      work: Work,
      game_id: GameId,
      position: Fen.Full,
      moves: String,
      variant: Variant
  ) derives Encoder.AsObject

object Lila:

  import ChessCirceCodecs.given

  case class Response(gameId: GameId, moves: String, uci: Uci):
    def sign  = moves.takeRight(20).replace(" ", "")
    def write = s"$gameId $sign ${uci.uci}"

  case class Request(
      id: GameId,
      initialFen: Fen.Full,
      variant: Variant,
      moves: String,
      level: Int,
      clock: Option[Clock]
  ) derives Codec.AsObject

  case class Clock(wtime: Int, btime: Int, inc: Int) derives Codec.AsObject

  def readMoveReq(msg: String): Option[Request] =
    msg.split(";", 6) match
      case Array(gameId, levelS, clockS, variantS, initialFenS, moves) =>
        levelS.toIntOption.map: level =>
          val variant    = chess.variant.Variant.orDefault(LilaKey(variantS))
          val initialFen = readFen(initialFenS).getOrElse(variant.initialFen)
          val clock      = readClock(clockS)
          Request(
            id = GameId(gameId),
            initialFen = initialFen,
            variant = variant,
            moves = moves,
            level = level,
            clock = clock
          )
      case _ => None

  def readFen(str: String): Option[Fen.Full] =
    Option.when(str.nonEmpty)(Fen.Full(str))

  def readClock(s: String): Option[Clock] =
    s.split(" ", 3) match
      case Array(ws, bs, incs) =>
        (ws.toIntOption, bs.toIntOption, incs.toIntOption).mapN(Clock.apply)
      case _ => None
