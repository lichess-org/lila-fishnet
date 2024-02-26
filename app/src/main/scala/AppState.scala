package lila.fishnet

import cats.syntax.all.*
import cats.effect.IO
import java.time.Instant
import lila.fishnet.Work.Move
import org.typelevel.log4cats.Logger

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

    def applyMove(monitor: Monitor, client: LilaClient)(workId: WorkId, apikey: ClientKey, move: BestMove)(
        using Logger[IO]
    ): (AppState, IO[Unit]) =
      state.get(workId) match
        case None =>
          state -> monitor.notFound(workId, apikey)
        case Some(work) if work.isAcquiredBy(apikey) =>
          move.uci match
            case Some(uci) =>
              state - work.id -> (monitor.success(work) >> client.send(
                Lila.Move(
                  work.request.id,
                  work.request.moves,
                  uci
                )
              ))
            case _ =>
              val newState = work.clearAssginedKey.fold(state)(state.updated(work.id, _))
              newState -> (Logger[IO].warn(s"Give up move: $work") >>
                monitor.failure(work, apikey, new Exception("Missing move")))
        case Some(move) =>
          state -> monitor.notAcquired(move, apikey)

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
