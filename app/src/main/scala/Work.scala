package lila.fishnet

import chess.variant.Variant
import chess.format.Fen
import java.time.Instant

object Work:

  case class Id(value: String) extends AnyVal with StringValue

  case class Acquired(clientKey: ClientKey, date: Instant):
    def ageInMillis       = System.currentTimeMillis - date.toEpochMilli
    override def toString = s"by $clientKey at $date"

  case class Game(id: String, initialFen: Option[Fen.Epd], variant: Variant, moves: String)

  case class Clock(wtime: Int, btime: Int, inc: Int)

  case class Move(
      _id: Work.Id, // random
      game: Game,
      level: Int,
      clock: Option[Work.Clock],
      tries: Int,
      lastTryByKey: Option[ClientKey],
      acquired: Option[Acquired],
      createdAt: Instant,
  ):

    def id                                 = _id
    def acquiredAt                         = acquired.map(_.date)
    def acquiredByKey: Option[ClientKey]   = acquired.map(_.clientKey)
    def isAcquiredBy(clientKey: ClientKey) = acquiredByKey contains clientKey
    def isAcquired                         = acquired.isDefined
    def nonAcquired                        = !isAcquired
    def canAcquire(clientKey: ClientKey)   = lastTryByKey.fold(true)(clientKey.!=)
    def acquiredBefore(date: Instant)      = acquiredAt.fold(false)(_.isBefore(date))

    def assignTo(clientKey: ClientKey, at: Instant) =
      copy(
        acquired = Some(
          Acquired(
            clientKey = clientKey,
            date = at,
          )
        ),
        lastTryByKey = Some(clientKey),
        tries = tries + 1,
      )

    def timeout = copy(acquired = None)
    def invalid = copy(acquired = None)

    def isOutOfTries = tries >= 3

    def similar(to: Move) = game.id == to.game.id && game.moves == to.game.moves

    override def toString =
      s"id:$id game:${game.id} variant:${game.variant.key} level:$level tries:$tries created:$createdAt acquired:$acquired"

  def makeId = Id(scala.util.Random.alphanumeric.take(8).mkString)
