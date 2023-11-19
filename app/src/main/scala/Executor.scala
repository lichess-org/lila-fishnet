package lila.fishnet

import cats.syntax.all.*
import cats.effect.IO
import cats.effect.kernel.Ref
import lila.fishnet.Work.Move
import java.time.Instant

trait FishnetClient:
  def acquire(accquire: MoveDb.Acquire): IO[Option[Work.Move]]
  def move(workId: Work.Id): IO[Option[Lila.Move]]

trait LilaClient:
  def send(move: Lila.Move): IO[Unit]

/**
 * Executor is responsible for:
 * store work in memory
 * - getting work from the queue
 * - sending work to lila
 * - adding work to the queue
 */
trait Executor:
  // get a move from the queue return Work
  def acquire(accquire: MoveDb.Acquire): IO[Option[Work.Move]]
  // get Work from Map => send to lila
  def move(workId: Work.Id, result: Fishnet.PostMove): IO[Unit]
  // add work to queue
  def add(work: Work.Move): IO[Unit]
  def clean(before: Instant): IO[Unit]

object Executor:

  val maxSize = 300

  type State = Map[Work.Id, Work.Move]
  def instance(client: LilaClient): IO[Executor] =
    Ref.of[IO, State](Map.empty).map: ref =>
      new Executor:

        def add(work: Work.Move): IO[Unit] =
          ref.update: m =>
            // if m.exists(_._2.similar(work)) then
            // logger.info(s"Add coll exist: $move")
            // clearIfFull()
            m + (work.id -> work)

        def acquire(accquire: MoveDb.Acquire): IO[Option[Work.Move]] =
          ref.modify: coll =>
            coll.values
              .foldLeft[Option[Move]](None):
                case (found, m) if m.nonAcquired =>
                  Some(found.fold(m): a =>
                    if m.canAcquire(accquire.clientKey) && m.createdAt.isBefore(a.createdAt) then m else a)
                case (found, _) => found
              .map: m =>
                val move = m.assignTo(accquire.clientKey)
                (coll + (move.id -> move)) -> move.some
              .getOrElse(coll -> none[Work.Move])

        def move(workId: Work.Id, data: Fishnet.PostMove): IO[Unit] =
          ref.flatModify: coll =>
            coll get workId match
              case None =>
                coll -> notFound(workId, data.fishnet.apikey)
              case Some(move) if move.isAcquiredBy(data.fishnet.apikey) =>
                data.move.uci match
                  case Some(uci) =>
                    coll - move.id -> (success(move) >> client.send(Lila.Move(move.game, uci)))
                  case _ =>
                    updateOrGiveUp(coll, move.invalid) ->
                      failure(move, data.fishnet.apikey, new Exception("Missing move"))
              case Some(move) =>
                coll -> notAcquired(move, data.fishnet.apikey)

        def clean(since: Instant): IO[Unit] =
          ref.update: coll =>
            val timedOut = coll.values.filter(_.acquiredBefore(since))
            // if (timedOut.nonEmpty)
            // logger.debug(s"cleaning ${timedOut.size} of ${coll.size} moves")
            timedOut.foldLeft(coll) { (coll, m) =>
              // logger.info(s"Timeout move $m")
              updateOrGiveUp(coll, m.timeout)
            }
          // monitor.dbSize.update(coll.size.toDouble)
          // monitor.dbQueued.update(coll.count(_._2.nonAcquired).toDouble)
          // monitor.dbAcquired.update(coll.count(_._2.isAcquired).toDouble)

        def clearIfFull(): IO[Unit] =
          ref.update: coll =>
            if coll.size > maxSize then
              // logger.warn(s"MoveDB collection is full! maxSize=$maxSize. Dropping all now!")
              Map.empty
            else
              coll

        def updateOrGiveUp(state: State, move: Work.Move): State =
          val newState = state - move.id
          if move.isOutOfTries then
            newState
          else
            newState + (move.id -> move)

        // report not found
        def notFound(id: Work.Id, key: ClientKey): IO[Unit] =
          IO.println(s"not found $id, $key")

        // report not acquired
        def notAcquired(work: Work.Move, key: ClientKey): IO[Unit] =
          IO.println(s"not acquired $work, $key")

        // success
        def success(move: Work.Move): IO[Unit] =
          IO.println(s"success $move")

        // failure
        def failure(move: Work.Move, client: ClientKey, ex: Throwable): IO[Unit] =
          IO.println(s"failure $move, $client, $ex")
