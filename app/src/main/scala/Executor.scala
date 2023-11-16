package lila.fishnet

import cats.syntax.all.*
import cats.effect.IO
import cats.effect.std.Queue
import cats.effect.kernel.Ref
import lila.fishnet.Work.Acquired
import org.joda.time.DateTime
import lila.fishnet.Work.Move

trait FishnetClient:
  def acquire(accquire: MoveDb.Acquire): IO[Option[Work]]
  def move(workId: Work.Id): IO[Option[Lila.Move]]

trait LilaClient:
  def send(move: Lila.Move): IO[Unit]

trait Executor:
  // get a move from the queue return Work
  def acquire(accquire: MoveDb.Acquire): IO[Option[Work.Move]]
  // get Work from Map => send to lila
  def move(workId: Work.Id, result: Fishnet.PostMove): IO[Unit]
  // add work to queue
  def add(work: Work.Move): IO[Unit]

object Executor:

  def instance(client: LilaClient): IO[Executor] =
    for
      workQueue <- Queue.unbounded[IO, Work.Move]
      waitingQueue <-
        Ref.of[IO, Map[Work.Id, Work.Move]](Map.empty) // Verify concurrent access with AtomicCell
    yield new Executor:
      type State = Map[Work.Id, Work.Move]

      def add(work: Work.Move): IO[Unit] =
        workQueue.offer(work)

      def acquire(accquire: MoveDb.Acquire): IO[Option[Work.Move]] =
        for
          work <- workQueue.tryTake
          acquiredWork = work.map(_.copy(acquired = Work.Acquired(accquire.clientKey, DateTime.now).some))
          _ <- acquiredWork.fold(IO.unit)(w => waitingQueue.update(_ + (w.id -> w)))
        yield acquiredWork

      def move(id: Work.Id, result: Fishnet.PostMove): IO[Unit] =
        waitingQueue.flatModify: m =>
          m.get(id).fold(m -> notFound(id, result.fishnet.apikey).void): work =>
            if work.isAcquiredBy(result.fishnet.apikey) then
              result.move.uci match
                case Some(uci) =>
                  val move = Lila.Move(work.game, uci)
                  (m - id) -> client.send(move)
                case _ => updateOrGiveUp(m, work.invalid)
            else
              m -> notAcquired(work, result.fishnet.apikey)

      def updateOrGiveUp(state: State, move: Work.Move): (State, IO[Unit]) =
        val newState = state - move.id
        val io = if move.isOutOfTries then
          workQueue.offer(move)
        else
          IO.unit
        newState -> io

      // report not found
      def notFound(id: Work.Id, key: ClientKey): IO[Unit] =
        IO.println(s"not found $id, $key")

      // report not acquired
      def notAcquired(work: Work.Move, key: ClientKey): IO[Unit] =
        IO.println(s"not acquired $work, $key")
