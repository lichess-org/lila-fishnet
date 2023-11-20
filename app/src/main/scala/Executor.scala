package lila.fishnet

import cats.syntax.all.*
import cats.effect.IO
import cats.effect.kernel.Ref
import lila.fishnet.Work.Move
import java.time.Instant
import lila.fishnet.Lila.Request

/**
 * Executor is responsible for:
 * store work in memory
 * - getting work from the queue
 * - sending work to lila
 * - adding work to the queue
 */
trait Executor:
  // get a move from the queue return Work
  def acquire(accquire: ClientKey): IO[Option[Work.RequestWithId]]
  // get Work from Map => send to lila
  def move(workId: WorkId, result: Fishnet.PostMove): IO[Unit]
  // add work to queue
  def add(work: Lila.Request): IO[Unit]
  def clean(before: Instant): IO[Unit]

object Executor:

  def apply(using client: LilaClient): IO[Executor] =
    instance(client)

  val maxSize = 300
  type State = Map[WorkId, Work.Move]

  def instance(client: LilaClient): IO[Executor] =
    Ref.of[IO, State](Map.empty).map: ref =>
      new Executor:

        def add(work: Request): IO[Unit] =
          fromRequest(work).flatMap: move =>
            ref.update: m =>
              // if m.exists(_._2.similar(work)) then
              // logger.info(s"Add coll exist: $move")
              clearIfFull(m) + (move.id -> move)

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

        def move(workId: WorkId, data: Fishnet.PostMove): IO[Unit] =
          ref.flatModify: coll =>
            coll get workId match
              case None =>
                coll -> notFound(workId, data.key)
              case Some(move) if move.isAcquiredBy(data.key) =>
                data.move.uci match
                  case Some(uci) =>
                    coll - move.id -> (success(move) >> client.send(Lila.Move(
                      move.request.id,
                      move.request.moves,
                      uci,
                    )))
                  case _ =>
                    updateOrGiveUp(coll, move.invalid) ->
                      failure(move, data.key, new Exception("Missing move"))
              case Some(move) =>
                coll -> notAcquired(move, data.key)

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

        def clearIfFull(coll: State): State =
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
        def notFound(id: WorkId, key: ClientKey): IO[Unit] =
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

  def fromRequest(req: Lila.Request): IO[Move] =
    (IO.delay(Work.makeId), IO.realTimeInstant).mapN: (id, now) =>
      Move(
        id = id,
        request = req,
        tries = 0,
        acquired = None,
        createdAt = now,
      )
