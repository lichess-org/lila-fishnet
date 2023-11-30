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

  val maxSize = 300
  type State = Map[WorkId, Work.Move]

  def instance(client: LilaClient, monitor: Monitor)(using Logger[IO]): IO[Executor] =
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
                      updateOrGiveUp(coll, work.invalid) ->
                        monitor.failure(work, apikey, new Exception("Missing move"))

                case Some(move) =>
                  coll -> monitor.notAcquired(move, apikey)

          def clean(since: Instant): IO[Unit] =
            ref
              .updateAndGet: coll =>
                val timedOut = coll.values.filter(_.acquiredBefore(since))
                // if (timedOut.nonEmpty)
                // logger.debug(s"cleaning ${timedOut.size} of ${coll.size} moves")
                timedOut.foldLeft(coll): (coll, m) =>
                  // logger.info(s"Timeout move $m")
                  updateOrGiveUp(coll, m.timeout)
              .flatMap(monitor.updateSize)

          def clearIfFull(coll: State): (State, IO[Unit]) =
            if coll.size > maxSize then
              Map.empty -> Logger[IO].warn(s"MoveDB collection is full! maxSize=$maxSize. Dropping all now!")
            else coll   -> IO.unit

          def updateOrGiveUp(state: State, move: Work.Move): State =
            val newState = state - move.id
            if move.isOutOfTries then newState
            else newState + (move.id -> move)

  def fromRequest(req: Lila.Request): IO[Move] =
    (IO.delay(Work.makeId), IO.realTimeInstant).mapN: (id, now) =>
      Move(
        id = id,
        request = req,
        tries = 0,
        acquired = None,
        createdAt = now
      )
