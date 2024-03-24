package lila.fishnet

import cats.effect.kernel.Resource
import cats.effect.{ IO, Ref }
import cats.syntax.all.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

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
  ): Executor = new:
    def add(work: Lila.Request): IO[Unit] =
      fromRequest(work).flatMap: task =>
        ref.flatModify: state =>
          val (newState, effect) =
            if state.isFull(config.maxSize) then
              def ids = state.tasks.map(t => t.id -> t.request.id).mkString(", ")
              AppState.empty ->
                warn"stateSize=${state.size} maxSize=${config.maxSize}. Dropping all! tasks: $ids"
            else state -> IO.unit
          newState.add(task) -> effect *> monitor.updateSize(newState)

    def acquire(key: ClientKey): IO[Option[Work.Task]] =
      IO.realTimeInstant.flatMap: at =>
        ref.modify(_.tryAcquire(key, at))

    def move(workId: WorkId, key: ClientKey, response: Option[BestMove]): IO[Unit] =
      info"move $response for $workId by $key" *>
        response.fold(invalidate(workId, key))(move(workId, key, _))

    private def move(workId: WorkId, key: ClientKey, response: BestMove): IO[Unit] =
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
                val (newState, maybeGivenUp) = state.unassignOrGiveUp(task)
                val logs = maybeGivenUp.traverse_(t => warn"Give up an invalid move $response by $key for $t")
                  *> failure(task, key)
                newState -> logs

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
