package lila.fishnet

import org.joda.time.DateTime

import chess.format.{ FEN, Uci }
import chess.variant.Variant

sealed trait Work {
  def _id: Work.Id
  def game: Work.Game
  def tries: Int
  def lastTryByKey: Option[ClientKey]
  def acquired: Option[Work.Acquired]
  def createdAt: DateTime

  def id = _id

  def acquiredAt                         = acquired.map(_.date)
  def acquiredByKey                      = acquired.map(_.clientKey)
  def isAcquiredBy(clientKey: ClientKey) = acquiredByKey contains clientKey
  def isAcquired                         = acquired.isDefined
  def nonAcquired                        = !isAcquired
  def canAcquire(clientKey: ClientKey)   = lastTryByKey.fold(true)(clientKey.!=)

  def acquiredBefore(date: DateTime) = acquiredAt.fold(false)(_ isBefore date)
}

object Work {

  case class Id(value: String) extends AnyVal with StringValue

  case class Acquired(
      clientKey: ClientKey,
      date: DateTime
  ) {

    def ageInMillis = Util.nowMillis - date.getMillis

    override def toString = s"by $clientKey at $date"
  }

  case class Game(
      id: String, // can be a study chapter ID, if studyId is set
      initialFen: Option[FEN],
      variant: Variant,
      moves: String
  ) {
    def ply = if (moves.isEmpty) 0 else moves.count(' '.==) + 1
  }

  case class Clock(wtime: Int, btime: Int, inc: Int)

  case class Move(
      _id: Work.Id, // random
      game: Game,
      level: Int,
      clock: Option[Work.Clock],
      tries: Int,
      lastTryByKey: Option[ClientKey],
      acquired: Option[Acquired],
      createdAt: DateTime
  ) extends Work {

    def assignTo(clientKey: ClientKey) = copy(
      acquired = Some(
        Acquired(
          clientKey = clientKey,
          date = DateTime.now
        )
      ),
      lastTryByKey = Some(clientKey),
      tries = tries + 1
    )

    def timeout = copy(acquired = None)
    def invalid = copy(acquired = None)

    def isOutOfTries = tries >= 3

    def similar(to: Move) = game.id == to.game.id && game.moves == to.game.moves

    override def toString =
      s"id:$id game:${game.id} variant:${game.variant.key} level:$level tries:$tries created:$createdAt acquired:$acquired"
  }

  def makeId = Id(scala.util.Random.alphanumeric.take(8).mkString)
}
