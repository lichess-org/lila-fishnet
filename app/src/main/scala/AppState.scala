package lila.fishnet

import cats.syntax.all.*
import lila.fishnet.Work.Task

import java.time.Instant

opaque type AppState = Map[WorkId, Work.Task]

enum GetTaskResult:
  case NotFound
  case Found(task: Work.Task)
  case AcquiredByOther(task: Work.Task)

object AppState:
  val empty: AppState = Map.empty

  def fromTasks(tasks: List[Work.Task]): AppState = tasks.map(t => t.id -> t).toMap

  extension (state: AppState)

    def tasks: List[Work.Task] = state.values.toList

    inline def isFull(maxSize: Int): Boolean =
      state.sizeIs >= maxSize

    inline def add(task: Task): AppState =
      state + (task.id -> task)

    inline def remove(id: WorkId): AppState =
      state - id

    inline def get(id: WorkId): Option[Work.Task] =
      state.get(id)

    inline def size: Int = state.size

    inline def count(p: Task => Boolean): Int = state.count((_, x) => p(x))

    def tryAcquire(key: ClientKey, at: Instant): (AppState, Option[Task]) =
      state.earliestNonAcquired
        .map: newTask =>
          val assignedTask = newTask.assignTo(key, at)
          state.updated(assignedTask.id, assignedTask) -> assignedTask.some
        .getOrElse(state -> none)

    def apply(workId: WorkId, key: ClientKey): GetTaskResult =
      state.get(workId) match
        case None                                 => GetTaskResult.NotFound
        case Some(task) if task.isAcquiredBy(key) => GetTaskResult.Found(task)
        case Some(task)                           => GetTaskResult.AcquiredByOther(task)

    def unassignOrGiveUp(candidates: List[Work.Task]): (AppState, List[Work.Task]) =
      candidates.foldLeft(state -> Nil):
        case ((state, xs), task) =>
          val (newState, maybeGivenUp) = state.unassignOrGiveUp(task)
          (newState, maybeGivenUp.fold(xs)(_ :: xs))

    def unassignOrGiveUp(task: Work.Task): (AppState, Option[Work.Task]) =
      task.clearAssignedKey match
        case None                 => (state - task.id, Some(task))
        case Some(unassignedTask) => (state.updated(task.id, unassignedTask), None)

    def acquiredBefore(since: Instant): List[Work.Task] =
      state.values.filter(_.acquiredBefore(since)).toList

    def earliestNonAcquired: Option[Work.Task] =
      state.values.filter(_.nonAcquired).minByOption(_.createdAt)
