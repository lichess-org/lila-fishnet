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
  // clean up all works that are acquired before the given time
  def clean(before: Instant): IO[Unit]

object Executor:

  import AppState.*

  def instance(client: LilaClient, monitor: Monitor, config: ExecutorConfig)(using Logger[IO]): IO[Executor] =
    Ref
      .of[IO, AppState](AppState.empty)
      .map: ref =>
        new Executor:

          def add(work: Request): IO[Unit] =
            fromRequest(work).flatMap: move =>
              ref.flatModify: state =>
                val (newState, effect) =
                  if state.isFull(config.maxSize) then
                    AppState.empty ->
                      Logger[IO].warn(s"StateSize=${state.size} maxSize=${config.maxSize}. Dropping all!")
                  else state -> IO.unit
                newState.addWork(move) -> effect

          def acquire(key: ClientKey): IO[Option[Work.RequestWithId]] =
            IO.realTimeInstant.flatMap: at =>
              ref.modify(_.tryAcquireMove(key, at))

          def move(workId: WorkId, apikey: ClientKey, move: BestMove): IO[Unit] =
            ref.flatModify: state =>
              state.get(workId) match
                case None => state -> Logger[IO].info(s"Received unknown work $workId by $apikey")
                case Some(work) if work.isAcquiredBy(apikey) =>
                  move.uci match
                    case Some(uci) =>
                      state - work.id -> (monitor.success(work) >>
                        client.send(Lila.Move(work.request.id, work.request.moves, uci)))
                    case _ =>
                      val newState = work.clearAssginedKey.fold(state)(state.updated(work.id, _))
                      newState -> (Logger[IO].warn(s"Give up move: $work") >>
                        Logger[IO].warn(s"Received invalid move $workId for ${work.request.id} by $apikey"))
                case Some(move) =>
                  state -> Logger[IO].info(
                    s"Received unacquired move ${workId} for ${move.request.id} by $apikey. Work current tries: ${move.tries} acquired: ${move.acquired}"
                  )

          def clean(since: Instant): IO[Unit] =
            ref.flatModify: state =>
              val timedOut                 = state.acquiredBefore(since)
              val logs                     = logIfTimedOut(state, timedOut)
              val (newState, gavedUpMoves) = state.updateOrGiveUp(timedOut)
              newState -> logs
                *> gavedUpMoves.traverse_(m => Logger[IO].warn(s"Give up move: $m"))
                *> monitor.updateSize(newState)

          private def logIfTimedOut(state: AppState, timeOut: List[Work.Move]): IO[Unit] =
            IO.whenA(timeOut.nonEmpty):
              Logger[IO].debug(s"cleaning ${timeOut.size} of ${state.size} moves")
                *> timeOut.traverse_(m => Logger[IO].info(s"Timeout move: $m"))

  def fromRequest(req: Lila.Request): IO[Move] =
    (IO(Work.makeId), IO.realTimeInstant).mapN: (id, now) =>
      Move(
        id = id,
        request = req,
        tries = 0,
        acquired = None,
        createdAt = now
      )
