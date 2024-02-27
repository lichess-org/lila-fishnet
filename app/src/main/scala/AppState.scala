package lila.fishnet

import cats.syntax.all.*
import java.time.Instant
import lila.fishnet.Work.Move

type AppState = Map[WorkId, Work.Move]

enum GetWorkResult:
  case NotFound
  case Found(move: Work.Move)
  case AcquiredByOther(move: Work.Move)

object AppState:
  val empty: AppState = Map.empty
  extension (state: AppState)

    def tryAcquireMove(key: ClientKey, at: Instant): (AppState, Option[Work.RequestWithId]) =
      state.earliestNonAcquiredMove
        .map: m =>
          val move = m.assignTo(key, at)
          state.updated(move.id, move) -> move.toRequestWithId.some
        .getOrElse(state -> none)

    def isFull(maxSize: Int): Boolean =
      state.sizeIs >= maxSize

    def addWork(move: Move): AppState =
      state + (move.id -> move)

    def acquiredBefore(since: Instant): List[Work.Move] =
      state.values.filter(_.acquiredBefore(since)).toList

    def earliestNonAcquiredMove: Option[Work.Move] =
      state.values.filter(_.nonAcquired).minByOption(_.createdAt)

    def getWork(workId: WorkId, key: ClientKey): GetWorkResult =
      state.get(workId) match
        case None                                 => GetWorkResult.NotFound
        case Some(move) if move.isAcquiredBy(key) => GetWorkResult.Found(move)
        case Some(move)                           => GetWorkResult.AcquiredByOther(move)

    def updateOrGiveUp(candidates: List[Work.Move]): (AppState, List[Work.Move]) =
      candidates.foldLeft[(AppState, List[Work.Move])](state -> Nil) { case ((state, xs), m) =>
        m.clearAssignedKey match
          case None       => (state - m.id, m :: xs)
          case Some(move) => (state.updated(m.id, move), xs)
      }
