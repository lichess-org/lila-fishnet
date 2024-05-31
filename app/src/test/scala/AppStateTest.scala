package lila.fishnet

import cats.Show
import weaver.scalacheck.Checkers

import java.time.Instant

import Arbitraries.given

object AppStateTest extends weaver.SimpleIOSuite with Checkers:

  given Show[AppState]             = Show.fromToString
  given Show[ClientKey]            = Show.fromToString
  given Show[Instant]              = Show.fromToString
  given Show[Work.Task]            = Show.fromToString
  given Show[Work.Task => Boolean] = Show.fromToString

  test("tasks.fromTasks == identity"):
    forall: (state: AppState) =>
      expect(AppState.fromTasks(state.tasks) == state)

  test("tasks.fromTasks == tasks"):
    forall: (ts: List[Work.Task]) =>
      val tasks = ts.distinctBy(_.id)
      expect(AppState.fromTasks(tasks).tasks.toSet == tasks.toSet)

  test("isFull"):
    forall: (state: AppState) =>
      expect(state.isFull(10) || state.size < 10)

  test("add = remove.add"):
    forall: (state: AppState, task: Work.Task) =>
      expect(state.remove(task.id).add(task) == state.add(task))

  test("add.remove == remove"):
    forall: (state: AppState, task: Work.Task) =>
      expect(state.add(task).remove(task.id) == state.remove(task.id))

  test("count(p) + count(!p) == size"):
    forall: (state: AppState, p: Work.Task => Boolean) =>
      expect(state.count(p) + state.count(x => !p(x)) == state.size)

  test("count(true) == size"):
    forall: (state: AppState) =>
      expect(state.count(_ => true) == state.size)

  test("count(false) == 0"):
    forall: (state: AppState) =>
      expect(state.count(_ => false) == 0)

  test("earliestNonAcquired is always non acquired"):
    forall: (state: AppState) =>
      expect(state.earliestNonAcquired.forall(_.nonAcquired))

  test("if earliestNonAcquired is defined then tryAcquire is defined"):
    forall: (state: AppState, key: ClientKey, at: Instant) =>
      expect(state.tryAcquire(key, at)._2.isDefined == state.earliestNonAcquired.isDefined)

  test("tryAcquire always acquires the earliest non acquired task"):
    forall: (state: AppState, key: ClientKey, at: Instant) =>
      expect:
        state.earliestNonAcquired.forall: nonAcquiredTask =>
          val (newState, ot) = state.tryAcquire(key, at)
          val task           = ot.get
          state.add(task) == newState && task == nonAcquiredTask.assignTo(key, at)

  test("tryAcquire then apply = Found"):
    forall: (state: AppState, key: ClientKey, at: Instant) =>
      val (newState, task) = state.tryAcquire(key, at)
      expect(task.forall(t => newState.apply(t.id, key) == GetTaskResult.Found(t)))

  test("acquiredBefore is acquiredBefore"):
    forall: (state: AppState, since: Instant) =>
      expect(state.acquiredBefore(since).forall(_.acquiredBefore(since)))

  test("updateOrGiveUp is a subset of given tasks"):
    forall: (state: AppState, before: Instant) =>
      val candidates   = state.acquiredBefore(before)
      val (_, givenUp) = state.unassignOrGiveUp(candidates)
      expect(givenUp.toSet.subsetOf(candidates.toSet))

  test("updateOrGiveUp preserves size"):
    forall: (state: AppState, before: Instant) =>
      val candidates          = state.acquiredBefore(before)
      val (newState, givenUp) = state.unassignOrGiveUp(candidates)
      expect(newState.size + givenUp.size == state.size)

  test("all given up tasks are outOfTries"):
    forall: (state: AppState, before: Instant) =>
      val candidates   = state.acquiredBefore(before)
      val (_, givenUp) = state.unassignOrGiveUp(candidates)
      expect(givenUp.forall(_.isOutOfTries))

  test("all candidates that are not given up are not outOfTries"):
    forall: (state: AppState, before: Instant) =>
      val candidates   = state.acquiredBefore(before)
      val (_, givenUp) = state.unassignOrGiveUp(candidates)
      val rest         = candidates.filterNot(givenUp.contains)
      expect(rest.forall(!_.isOutOfTries))

  test("after cleanup, acquiredBefore is empty"):
    forall: (state: AppState, before: Instant) =>
      val candidates    = state.acquiredBefore(before)
      val (newState, _) = state.unassignOrGiveUp(candidates)
      expect(newState.acquiredBefore(before).isEmpty)
