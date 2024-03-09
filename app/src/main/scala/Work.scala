package lila.fishnet

import io.circe.{ Codec, Decoder, Encoder }

import java.time.Instant

object Work:

  case class Acquired(clientKey: ClientKey, date: Instant) derives Codec.AsObject:
    override def toString = s"by $clientKey at $date"

  case class Task(
      id: WorkId,
      request: Lila.Request,
      tries: Int,
      acquired: Option[Acquired],
      createdAt: Instant
  ) derives Codec.AsObject:

    def acquiredAt: Option[Instant]                 = acquired.map(_.date)
    def isAcquired: Boolean                         = acquired.isDefined
    def nonAcquired: Boolean                        = !isAcquired
    def isAcquiredBy(clientKey: ClientKey): Boolean = acquired.exists(_.clientKey == clientKey)
    def acquiredBefore(date: Instant): Boolean      = acquired.exists(_.date.isBefore(date))

    def assignTo(clientKey: ClientKey, at: Instant) =
      copy(acquired = Some(Acquired(clientKey = clientKey, date = at)), tries = tries + 1)

    def isOutOfTries = tries >= 3

    // returns the move without the acquired key if it's not out of tries
    def clearAssignedKey: Option[Work.Task] =
      Option.when(!isOutOfTries)(copy(acquired = None))

    def toResponse =
      Fishnet.WorkResponse(
        work = Fishnet.Work(id = id, level = request.level, clock = request.clock),
        game_id = request.id,
        position = request.initialFen,
        moves = request.moves,
        variant = request.variant
      )

    override def toString =
      s"id:$id game:${request.id} variant:${request.variant.key} level:${request.level} tries:$tries created:$createdAt acquired:$acquired move: ${request.moves}"
