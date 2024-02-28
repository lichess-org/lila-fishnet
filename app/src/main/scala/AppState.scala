package lila.fishnet

import cats.syntax.all.*
import java.time.Instant
import lila.fishnet.Work.Task

type AppState = Map[WorkId, Work.Task]

enum GetTaskResult:
  case NotFound
  case Found(task: Work.Task)
  case AcquiredByOther(task: Work.Task)

object AppState:
  val empty: AppState = Map.empty
  extension (state: AppState)

    def tryAcquireTask(key: ClientKey, at: Instant): (AppState, Option[Task]) =
      state.earliestNonAcquiredTask
        .map: newTask =>
          val assignedTask = newTask.assignTo(key, at)
          state.updated(assignedTask.id, assignedTask) -> assignedTask.some
        .getOrElse(state -> none)

    def isFull(maxSize: Int): Boolean =
      state.sizeIs >= maxSize

    def addTask(move: Task): AppState =
      state + (move.id -> move)

    def acquiredBefore(since: Instant): List[Work.Task] =
      state.values.filter(_.acquiredBefore(since)).toList

    def earliestNonAcquiredTask: Option[Work.Task] =
      state.values.filter(_.nonAcquired).minByOption(_.createdAt)

    def apply(workId: WorkId, key: ClientKey): GetTaskResult =
      state.get(workId) match
        case None                                 => GetTaskResult.NotFound
        case Some(task) if task.isAcquiredBy(key) => GetTaskResult.Found(task)
        case Some(task)                           => GetTaskResult.AcquiredByOther(task)

    def updateOrGiveUp(candidates: List[Work.Task]): (AppState, List[Work.Task]) =
      candidates.foldLeft[(AppState, List[Work.Task])](state -> Nil) { case ((state, xs), task) =>
        task.clearAssignedKey match
          case None                 => (state - task.id, task :: xs)
          case Some(unAssignedTask) => (state.updated(task.id, unAssignedTask), xs)
      }
