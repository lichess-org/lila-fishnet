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
              val (newState, failedMove) = state.updateOrGiveUp(work.invalid)
              newState -> (Logger[IO].warn(s"Give up move: $failedMove") >>
                monitor.failure(work, apikey, new Exception("Missing move")))
        case Some(move) =>
          state -> monitor.notAcquired(move, apikey)

    def clean(monitor: Monitor)(since: Instant)(using Logger[IO]): (AppState, IO[Unit]) =
      val timedOut: List[Work.Move] = state.values.filter(_.acquiredBefore(since)).toList
      val logIfTimedOut =
        if timedOut.nonEmpty then
          Logger[IO].debug(s"cleaning ${timedOut.size} of ${state.size} moves") >>
            timedOut.traverse_(m => Logger[IO].info(s"Timeout move: $m"))
        else IO.unit
      val (newState, gavedUpMoves) = timedOut.foldLeft[(AppState, List[Work.Move])](state -> Nil): (x, m) =>
        val (newState, move) = x._1.updateOrGiveUp(m.timeout)
        (newState, move.fold(x._2)(_ :: x._2))
      newState -> logIfTimedOut
        *> gavedUpMoves.traverse_(m => Logger[IO].warn(s"Give up move: $m"))
        *> monitor.updateSize(newState)

    def earliestNonAcquiredMove: Option[Work.Move] =
      state.values.filter(_.nonAcquired).minByOption(_.createdAt)

    def updateOrGiveUp(move: Work.Move): (AppState, Option[Work.Move]) =
      val newState = state - move.id
      if move.isOutOfTries then (newState, move.some)
      else (newState + (move.id -> move), none)
