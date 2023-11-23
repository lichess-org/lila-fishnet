package lila.fishnet

import java.time.Instant

object Work:

  case class RequestWithId(id: WorkId, request: Lila.Request):
    def toResponse =
      Fishnet.WorkResponse(
        work = Fishnet.Work(id = id, level = request.level, clock = request.clock),
        game_id = request.id.value,
        position = request.initialFen,
        moves = request.moves,
        variant = request.variant
      )

  case class Acquired(clientKey: ClientKey, date: Instant):
    override def toString = s"by $clientKey at $date"

  case class Move(
      id: WorkId,
      request: Lila.Request,
      tries: Int,
      acquired: Option[Acquired],
      createdAt: Instant
  ):

    def toRequestWithId =
      RequestWithId(id, request)

    def acquiredAt                         = acquired.map(_.date)
    def acquiredByKey: Option[ClientKey]   = acquired.map(_.clientKey)
    def isAcquiredBy(clientKey: ClientKey) = acquiredByKey contains clientKey
    def isAcquired                         = acquired.isDefined
    def nonAcquired                        = !isAcquired
    def canAcquire(clientKey: ClientKey)   = acquired.fold(true)(_.clientKey != clientKey)
    def acquiredBefore(date: Instant)      = acquiredAt.fold(false)(_.isBefore(date))

    def assignTo(clientKey: ClientKey, at: Instant) =
      copy(acquired = Some(Acquired(clientKey = clientKey, date = at)), tries = tries + 1)

    def timeout = copy(acquired = None)
    def invalid = copy(acquired = None)

    def isOutOfTries = tries >= 3

    def similar(to: Move) = request.id == to.request.id && request.moves == to.request.moves

    override def toString =
      s"id:$id game:${request.id} variant:${request.variant.key} level:${request.level} tries:$tries created:$createdAt acquired:$acquired"

  def makeId = WorkId(scala.util.Random.alphanumeric.take(8).mkString)
