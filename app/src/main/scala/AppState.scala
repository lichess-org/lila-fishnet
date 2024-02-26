package lila.fishnet

import cats.syntax.all.*
import java.time.Instant
import lila.fishnet.Work.Move

type AppState = Map[WorkId, Work.Move]

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
      state.size >= maxSize

    def addWork(move: Move): AppState =
      state + (move.id -> move)

    def acquiredBefore(since: Instant): List[Work.Move] =
      state.values.filter(_.acquiredBefore(since)).toList

    def earliestNonAcquiredMove: Option[Work.Move] =
      state.values.filter(_.nonAcquired).minByOption(_.createdAt)

    def updateOrGiveUp(candidates: List[Work.Move]): (AppState, List[Work.Move]) =
      candidates.foldLeft[(AppState, List[Work.Move])](state -> Nil) { case ((state, xs), m) =>
        m.clearAssginedKey match
          case None       => (state - m.id, m :: xs)
          case Some(move) => (state.updated(m.id, move), xs)
      }
