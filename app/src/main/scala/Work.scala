package lila.fishnet

import chess.variant.Variant
import chess.format.Fen
import java.time.Instant
import io.circe.{ Codec, Decoder, Encoder }

object Work:

  case class Id(value: String) derives Codec.AsObject

  case class RequestWithId(id: Id, request: Lila.Request) derives Encoder.AsObject

  case class Acquired(clientKey: ClientKey, date: Instant):
    override def toString = s"by $clientKey at $date"

  case class Game(id: String, initialFen: Option[Fen.Epd], variant: Variant, moves: String)
      derives Encoder.AsObject

  given Encoder[Fen.Epd] = Encoder.encodeString.contramap(_.value)
  given Encoder[Variant] = Encoder.encodeString.contramap(_.name)

  case class Clock(wtime: Int, btime: Int, inc: Int) derives Codec.AsObject

  case class Move(
      id: Work.Id, // random
      game: Game,
      level: Int,
      clock: Option[Work.Clock],
      tries: Int,
      acquired: Option[Acquired],
      createdAt: Instant,
  ):

    def toRequest =
      Lila.Request(
        game = game,
        level = level,
        clock = clock,
      )

    def toRequestWithId =
      RequestWithId(id, toRequest)

    def acquiredAt                         = acquired.map(_.date)
    def acquiredByKey: Option[ClientKey]   = acquired.map(_.clientKey)
    def isAcquiredBy(clientKey: ClientKey) = acquiredByKey contains clientKey
    def isAcquired                         = acquired.isDefined
    def nonAcquired                        = !isAcquired
    def canAcquire(clientKey: ClientKey)   = acquired.fold(true)(_.clientKey != clientKey)
    def acquiredBefore(date: Instant)      = acquiredAt.fold(false)(_.isBefore(date))

    def assignTo(clientKey: ClientKey, at: Instant) =
      copy(
        acquired = Some(
          Acquired(
            clientKey = clientKey,
            date = at,
          )
        ),
        tries = tries + 1,
      )

    def timeout = copy(acquired = None)
    def invalid = copy(acquired = None)

    def isOutOfTries = tries >= 3

    def similar(to: Move) = game.id == to.game.id && game.moves == to.game.moves

    override def toString =
      s"id:$id game:${game.id} variant:${game.variant.key} level:$level tries:$tries created:$createdAt acquired:$acquired"

  def makeId = Id(scala.util.Random.alphanumeric.take(8).mkString)
