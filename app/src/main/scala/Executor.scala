package lila.fishnet

import cats.effect.kernel.Resource
import cats.effect.{ IO, Ref }
import cats.syntax.all.*
import org.typelevel.log4cats.Logger

import java.time.Instant

/** Executor is responsible for: store work in memory
  *   - getting work from the queue
  *   - sending work to lila
  *   - adding work to the queue
  */
trait Executor:
  // fishnet client tries to get an unassigned task
  def acquire(accquire: ClientKey): IO[Option[Work.Task]]
  // fishnet client sends the best move for it's assigned task
  def move(workId: WorkId, fishnetKey: ClientKey, move: BestMove): IO[Unit]
  // Lila sends a position
  def add(work: Lila.Request): IO[Unit]
  // clean up all works that are acquired before a given time
  // this is to prevent tasks from being stuck in the queue
  // this will be called periodically
  def clean(before: Instant): IO[Unit]

object Executor:

  import AppState.*

  def instance(client: LilaClient, repository: StateRepository, monitor: Monitor, config: ExecutorConfig)(
      using Logger[IO]
  ): Resource[IO, Executor] =
    Ref
      .of[IO, AppState](AppState.empty)
      .toResource
      .flatMap: ref =>
        Resource
          .make(repository.get.flatMap(ref.set))(_ => ref.get.flatMap(repository.save))
          .as(instance(ref, client, monitor, config))

  def instance(ref: Ref[IO, AppState], client: LilaClient, monitor: Monitor, config: ExecutorConfig)(using
      Logger[IO]
  ): Executor =
    new Executor:
      def add(work: Lila.Request): IO[Unit] =
        fromRequest(work).flatMap: task =>
          ref.flatModify: state =>
            val (newState, effect) =
              if state.isFull(config.maxSize) then
                AppState.empty ->
                  Logger[IO].warn(s"stateSize=${state.size} maxSize=${config.maxSize}. Dropping all!")
              else state -> IO.unit
            newState.add(task) -> effect *> monitor.updateSize(newState)

      def acquire(key: ClientKey): IO[Option[Work.Task]] =
        IO.realTimeInstant.flatMap: at =>
          ref.modify(_.tryAcquire(key, at))

      def move(workId: WorkId, key: ClientKey, response: BestMove): IO[Unit] =
        ref.flatModify: state =>
          state(workId, key) match
            case GetTaskResult.NotFound              => state -> logNotFound(workId, key)
            case GetTaskResult.AcquiredByOther(task) => state -> logNotAcquired(task, key)
            case GetTaskResult.Found(task) =>
              response.uci match
                case Some(uci) =>
                  state.remove(task.id) -> (monitor.success(task) >>
                    client.send(Lila.Response(task.request.id, task.request.moves, uci)))
                case _ =>
                  val (newState, maybeGivenUp) = state.clearOrGiveUp(task)
                  val io = maybeGivenUp.traverse(task => Logger[IO].warn(
                    s"Give up move due to invalid move $response by $key for $task"
                  ))
                  newState -> io *> failure(task, key)

      def clean(since: Instant): IO[Unit] =
        ref.flatModify: state =>
          val timedOut                 = state.acquiredBefore(since)
          val timedOutLogs             = logTimedOut(state, timedOut)
          val (newState, gavedUpMoves) = state.updateOrGiveUp(timedOut)
          newState -> timedOutLogs
            *> gavedUpMoves.traverse_(m => Logger[IO].warn(s"Give up move due to clean up: $m"))
            *> monitor.updateSize(newState)

      private def logTimedOut(state: AppState, timeOut: List[Work.Task]): IO[Unit] =
        IO.whenA(timeOut.nonEmpty):
          Logger[IO].info(s"cleaning ${timeOut.size} of ${state.size} moves")
            *> timeOut.traverse_(m => Logger[IO].info(s"Timeout move: $m"))

      private def failure(work: Work.Task, clientKey: ClientKey) =
        Logger[IO].warn(s"Received invalid move ${work.id} for ${work.request.id} by $clientKey")

      private def logNotFound(id: WorkId, clientKey: ClientKey) =
        Logger[IO].info(s"Received unknown work $id by $clientKey")

      private def logNotAcquired(work: Work.Task, clientKey: ClientKey) =
        Logger[IO].info(
          s"Received unacquired move ${work.id} for ${work.request.id} by $clientKey. Work current tries: ${work.tries} acquired: ${work.acquired}"
        )

  def fromRequest(req: Lila.Request): IO[Work.Task] =
    (makeId, IO.realTimeInstant).mapN: (id, now) =>
      Work.Task(
        id = id,
        request = req,
        tries = 0,
        acquired = None,
        createdAt = now
      )

  def makeId: IO[WorkId] = IO(WorkId(scala.util.Random.alphanumeric.take(8).mkString))
