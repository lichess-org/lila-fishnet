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

object Executor:

  type State = Map[WorkId, Work.Move]

  def instance(client: LilaClient, monitor: Monitor, config: ExecutorConfig)(using Logger[IO]): IO[Executor] =
    Ref
      .of[IO, State](Map.empty)
      .map: ref =>
        new Executor:

          def add(work: Request): IO[Unit] =
            fromRequest(work).flatMap: move =>
              ref.flatModify: m =>
                val (x, o) = clearIfFull(m)
                x + (move.id -> move) -> o

          def acquire(key: ClientKey): IO[Option[Work.RequestWithId]] =
            IO.realTimeInstant.flatMap: at =>
              ref.modify: coll =>
                coll.values
                  .foldLeft[Option[Work.Move]](none):
                    case (found, m) if m.nonAcquired =>
                      Some(found.fold(m): a =>
                        if m.canAcquire(key) && m.createdAt.isBefore(a.createdAt) then m else a)
                    case (found, _) => found
                  .map: m =>
                    val move = m.assignTo(key, at)
                    (coll + (move.id -> move)) -> move.toRequestWithId.some
                  .getOrElse(coll -> none)

          def move(workId: WorkId, apikey: ClientKey, move: BestMove): IO[Unit] =
            ref.flatModify: coll =>
              coll get workId match
                case None =>
                  coll -> monitor.notFound(workId, apikey)
                case Some(work) if work.isAcquiredBy(apikey) =>
                  move.uci match
                    case Some(uci) =>
                      coll - work.id -> (monitor.success(work) >> client.send(
                        Lila.Move(
                          work.request.id,
                          work.request.moves,
                          uci
                        )
                      ))
                    case _ =>
                      val (state, failedMove) = updateOrGiveUp(coll, work.invalid)
                      state -> (Logger[IO].warn(s"Give up move: $failedMove") >>
                        monitor.failure(work, apikey, new Exception("Missing move")))

                case Some(move) =>
                  coll -> monitor.notAcquired(move, apikey)

          def clean(since: Instant): IO[Unit] =
            ref
              .flatModify: coll =>
                val timedOut = coll.values.filter(_.acquiredBefore(since)).toList
                val logIfTimedOut =
                  if timedOut.nonEmpty then
                    Logger[IO].debug(s"cleaning ${timedOut.size} of ${coll.size} moves") >>
                      timedOut.traverse_(m => Logger[IO].info(s"Timeout move: $m"))
                  else IO.unit
                val (state, gavedUpMoves) = timedOut.foldLeft[(State, List[Work.Move])](coll -> Nil):
                  (x, m) =>
                    val (newState, move) = updateOrGiveUp(x._1, m.timeout)
                    (newState, move.fold(x._2)(_ :: x._2))

                state -> (logIfTimedOut *> gavedUpMoves
                  .traverse_(m => Logger[IO].warn(s"Give up move: $m"))
                  .as(state))
              .flatMap(monitor.updateSize)

          def clearIfFull(coll: State): (State, IO[Unit]) =
            if coll.size >= config.maxSize then
              Map.empty -> Logger[IO].warn(
                s"MoveDB collection is full! maxSize=${config.maxSize}. Dropping all now!"
              )
            else coll -> IO.unit

          def updateOrGiveUp(state: State, move: Work.Move): (State, Option[Work.Move]) =
            val newState = state - move.id
            if move.isOutOfTries then (newState, move.some)
            else (newState + (move.id -> move), none)

  def fromRequest(req: Lila.Request): IO[Move] =
    (IO.delay(Work.makeId), IO.realTimeInstant).mapN: (id, now) =>
      Move(
        id = id,
        request = req,
        tries = 0,
        acquired = None,
        createdAt = now
      )
