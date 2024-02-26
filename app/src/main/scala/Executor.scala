package lila.fishnet

import cats.effect.IO
import cats.effect.kernel.Ref
import cats.syntax.all.*
import java.time.Instant
import lila.fishnet.Lila.Request
import lila.fishnet.Work.Move
import org.typelevel.log4cats.Logger

/** Executor is responsible for: store work in memory
  *   - getting work from the queue
  *   - sending work to lila
  *   - adding work to the queue
  */
trait Executor:
  // get a move from the queue return Work
  def acquire(accquire: ClientKey): IO[Option[Work.RequestWithId]]
  // get Work from Map => send to lila
  def move(workId: WorkId, fishnetKey: ClientKey, move: BestMove): IO[Unit]
  // add work to queue
  def add(work: Lila.Request): IO[Unit]
  def clean(before: Instant): IO[Unit]

type State = Map[WorkId, Work.Move]

object State:
  val empty: State = Map.empty
  extension (state: State)

    def tryAcquireMove(key: ClientKey, at: Instant): (State, Option[Work.RequestWithId]) =
      state.earliestNonAcquiredMove
        .map: m =>
          val move = m.assignTo(key, at)
          state.updated(move.id, move) -> move.toRequestWithId.some
        .getOrElse(state -> none)

    def clearIfFull(maxSize: Int)(using Logger[IO]): (State, IO[Unit]) =
      if state.size >= maxSize then
        Map.empty -> Logger[IO].warn(s"MoveDB collection is full! maxSize=${maxSize}. Dropping all now!")
      else state  -> IO.unit

    def addWork(move: Move, maxSize: Int)(using Logger[IO]): (State, IO[Unit]) =
      val (newState, effect) = state.clearIfFull(maxSize)
      newState + (move.id -> move) -> effect

    def applyMove(monitor: Monitor, client: LilaClient)(workId: WorkId, apikey: ClientKey, move: BestMove)(
        using Logger[IO]
    ): (State, IO[Unit]) =
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

    def earliestNonAcquiredMove: Option[Work.Move] =
      state.values.filter(_.nonAcquired).minByOption(_.createdAt)

    def updateOrGiveUp(move: Work.Move): (State, Option[Work.Move]) =
      val newState = state - move.id
      if move.isOutOfTries then (newState, move.some)
      else (newState + (move.id -> move), none)

object Executor:

  import State.*

  def instance(client: LilaClient, monitor: Monitor, config: ExecutorConfig)(using Logger[IO]): IO[Executor] =
    Ref
      .of[IO, State](Map.empty)
      .map: ref =>
        new Executor:

          def add(work: Request): IO[Unit] =
            fromRequest(work).flatMap: move =>
              ref.flatModify(_.addWork(move, config.maxSize))

          def acquire(key: ClientKey): IO[Option[Work.RequestWithId]] =
            IO.realTimeInstant.flatMap: at =>
              ref.modify(_.tryAcquireMove(key, at))

          def move(workId: WorkId, apikey: ClientKey, move: BestMove): IO[Unit] =
            ref.flatModify(_.applyMove(monitor, client)(workId, apikey, move))

          def clean(since: Instant): IO[Unit] =
            ref
              .flatModify: map =>
                val timedOut = map.values.filter(_.acquiredBefore(since)).toList
                val logIfTimedOut =
                  if timedOut.nonEmpty then
                    Logger[IO].debug(s"cleaning ${timedOut.size} of ${map.size} moves") >>
                      timedOut.traverse_(m => Logger[IO].info(s"Timeout move: $m"))
                  else IO.unit
                val (state, gavedUpMoves) = timedOut.foldLeft[(State, List[Work.Move])](map -> Nil): (x, m) =>
                  val (newState, move) = x._1.updateOrGiveUp(m.timeout)
                  (newState, move.fold(x._2)(_ :: x._2))

                state -> (logIfTimedOut *> gavedUpMoves
                  .traverse_(m => Logger[IO].warn(s"Give up move: $m"))
                  .as(state))
              .flatMap(monitor.updateSize)

  def fromRequest(req: Lila.Request): IO[Move] =
    (IO(Work.makeId), IO.realTimeInstant).mapN: (id, now) =>
      Move(
        id = id,
        request = req,
        tries = 0,
        acquired = None,
        createdAt = now
      )
