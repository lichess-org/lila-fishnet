package lila.fishnet

import cats.effect.kernel.Resource
import cats.effect.{ IO, Ref }
import cats.syntax.all.*
import org.typelevel.log4cats.syntax.*
import org.typelevel.log4cats.{ Logger, LoggerFactory }

import java.time.Instant

/** Executor is responsible for: store work in memory
  *   - getting work from the queue
  *   - sending work to lila
  *   - adding work to the queue
  */
trait Executor:
  // Get an unassigned task from the queue
  // If there is no unassigned task, return None
  def acquire(accquire: ClientKey): IO[Option[Work.Task]]
  // fishnet client sends the best move for it's assigned task
  // move is none if it's assigned task is invalid or the game is already finished
  def move(workId: WorkId, key: ClientKey, move: Option[BestMove]): IO[Unit]
  // Receive a new work from Lila
  def add(work: Lila.Request): IO[Unit]
  // clean up all works that are acquired before a given time
  // this is to prevent tasks from being stuck in the queue
  // this will be called periodically
  def clean(before: Instant): IO[Unit]

object Executor:

  import AppState.*

  def instance(client: LilaClient, monitor: Monitor, config: ExecutorConfig)(using
      LoggerFactory[IO]
  ): Resource[IO, Executor] =
    Ref
      .of[IO, AppState](AppState.empty)
      .toResource
      .map(instance(client, monitor, config))

  def instance(client: LilaClient, monitor: Monitor, config: ExecutorConfig)(ref: Ref[IO, AppState])(using
      LoggerFactory[IO]
  ): Executor = new:
    given Logger[IO] = LoggerFactory.getLoggerFromName("Executor")
    def add(work: Lila.Request): IO[Unit] =
      fromRequest(work).flatMap: task =>
        ref.flatModify: state =>
          val (newState, effect) =
            if state.isFull(config.maxSize) then
              AppState.empty -> warn"stateSize=${state.size} maxSize=${config.maxSize}. Dropping all!"
            else state       -> IO.unit
          newState.add(task) -> effect *> monitor.updateSize(newState)

    def acquire(key: ClientKey): IO[Option[Work.Task]] =
      IO.realTimeInstant.flatMap: at =>
        ref.modify(_.tryAcquire(key, at))

    def move(workId: WorkId, key: ClientKey, response: Option[BestMove]): IO[Unit] =
      response.fold(invalidate(workId, key))(move(workId, key, _))

    private def move(workId: WorkId, key: ClientKey, response: BestMove): IO[Unit] =
      ref.flatModify: state =>
        state(workId, key) match
          case GetTaskResult.NotFound              => state -> logNotFound(workId, key)
          case GetTaskResult.AcquiredByOther(task) => state -> logNotAcquired(task, key)
          case GetTaskResult.Found(task) =>
            state.remove(task.id) -> client
              .send(Lila.Response(task.request.id, task.request.moves, response.value))
              .handleErrorWith: e =>
                error"Failed to send move ${task.id} for ${task.request.id} by $key: $e"
                  *> failure(task, key)
            *> monitor.success(task)

    private def invalidate(workId: WorkId, key: ClientKey): IO[Unit] =
      ref.flatModify: state =>
        state.remove(workId) ->
          state
            .get(workId)
            .fold(warn"unknown and invalid work from $key"): task =>
              warn"invalid lila work $task from $key"

    def clean(since: Instant): IO[Unit] =
      ref.flatModify: state =>
        val timedOut                 = state.acquiredBefore(since)
        val timedOutLogs             = logTimedOut(state, timedOut)
        val (newState, gavedUpMoves) = state.unassignOrGiveUp(timedOut)
        newState -> timedOutLogs
          *> gavedUpMoves.traverse_(m => warn"Give up move due to clean up: $m")
          *> monitor.updateSize(newState)

    private def logTimedOut(state: AppState, timeOut: List[Work.Task]): IO[Unit] =
      IO.whenA(timeOut.nonEmpty):
        info"cleaning ${timeOut.size} of ${state.size} moves"
          *> timeOut.traverse_(m => info"Timeout move: $m")

    private def failure(work: Work.Task, clientKey: ClientKey) =
      warn"Received invalid move ${work.id} for ${work.request.id} by $clientKey"

    private def logNotFound(id: WorkId, clientKey: ClientKey) =
      info"Received unknown work $id by $clientKey"

    private def logNotAcquired(work: Work.Task, clientKey: ClientKey) =
      info"Received unacquired move ${work.id} for ${work.request.id} by $clientKey. Work current tries: ${work.tries} acquired: ${work.acquired}"

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
