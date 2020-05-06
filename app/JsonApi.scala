package lila.fishnet

import org.joda.time.DateTime
import play.api.libs.json._

import chess.format.{ FEN, Uci }
import chess.variant.Variant
import lila.fishnet.{ Work => W }

object JsonApi {

  sealed trait Request {
    val fishnet: Request.Fishnet
    def clientKey = fishnet.apikey
  }

  object Request {

    sealed trait Result

    case class Fishnet(apikey: ClientKey)

    case class Acquire(fishnet: Fishnet) extends Request

    case class PostMove(fishnet: Fishnet, move: MoveResult) extends Request with Result

    case class MoveResult(bestmove: String) {
      def uci: Option[Uci] = Uci(bestmove)
    }
  }

  case class Game(
      game_id: String,
      position: FEN,
      variant: Variant,
      moves: String
  )

  def fromGame(g: W.Game) =
    Game(
      game_id = g.id,
      position = g.initialFen | FEN(g.variant.initialFen),
      variant = g.variant,
      moves = g.moves
    )

  sealed trait Work {
    val id: String
    val game: Game
  }
  case class Move(
      id: String,
      level: Int,
      game: Game,
      clock: Option[Work.Clock]
  ) extends Work

  def moveFromWork(m: Work.Move) = Move(m.id.value, m.level, fromGame(m.game), m.clock)

  object readers {
    import play.api.libs.functional.syntax._
    implicit val ClientKeyReads  = Reads.of[String].map(new ClientKey(_))
    implicit val FishnetReads    = Json.reads[Request.Fishnet]
    implicit val AcquireReads    = Json.reads[Request.Acquire]
    implicit val MoveResultReads = Json.reads[Request.MoveResult]
    implicit val PostMoveReads   = Json.reads[Request.PostMove]
  }

  object writers {
    implicit val VariantWrites                   = Writes[Variant] { v => JsString(v.key) }
    implicit val FENWrites                       = Writes[FEN] { fen => JsString(fen.value) }
    implicit val GameWrites: Writes[Game]        = Json.writes[Game]
    implicit val ClockWrites: Writes[Work.Clock] = Json.writes[Work.Clock]
    implicit val WorkIdWrites                    = Writes[Work.Id] { id => JsString(id.value) }
    implicit val WorkWrites = OWrites[Work] { work =>
      (work match {
        case m: Move =>
          Json.obj(
            "work" -> Json.obj(
              "type"  -> "move",
              "id"    -> m.id,
              "level" -> m.level,
              "clock" -> m.clock
            )
          )
      }) ++ Json.toJson(work.game).as[JsObject]
    }
  }
}
