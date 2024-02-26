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

  import AppState.*

  def instance(client: LilaClient, monitor: Monitor, config: ExecutorConfig)(using Logger[IO]): IO[Executor] =
    Ref
      .of[IO, AppState](Map.empty)
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
            ref.flatModify(_.clean(monitor)(since))

  def fromRequest(req: Lila.Request): IO[Move] =
    (IO(Work.makeId), IO.realTimeInstant).mapN: (id, now) =>
      Move(
        id = id,
        request = req,
        tries = 0,
        acquired = None,
        createdAt = now
      )
