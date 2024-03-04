package lila.fishnet

import munit.ScalaCheckSuite
import org.scalacheck.Prop.{ forAll, propBoolean }

import java.time.Instant

import Arbitraries.given

class AppStateTest extends ScalaCheckSuite:

  test("isFull"):
    forAll: (state: AppState) =>
      state.isFull(10) || state.size < 10

  test("add = remove.add"):
    forAll: (state: AppState, task: Work.Task) =>
      state.remove(task.id).add(task) == state.add(task)

  test("add.remove == remove"):
    forAll: (state: AppState, task: Work.Task) =>
      state.add(task).remove(task.id) == state.remove(task.id)

  test("count(p) + count(!p) == size"):
    forAll: (state: AppState, p: Work.Task => Boolean) =>
      state.count(p) + state.count(x => !p(x)) == state.size

  test("count(true) == size"):
    forAll: (state: AppState) =>
      state.count(_ => true) == state.size

  test("count(false) == 0"):
    forAll: (state: AppState) =>
      state.count(_ => false) == 0

  test("earliestNonAcquired is always non acquired"):
    forAll: (state: AppState) =>
      state.earliestNonAcquired.forall(_.nonAcquired)

  test("if earliestNonAcquired is defined then tryAcquire is defined"):
    forAll: (state: AppState, key: ClientKey, at: Instant) =>
      state.tryAcquire(key, at)._2.isDefined == state.earliestNonAcquired.isDefined

  test("tryAcquire always acquires the earliest non acquired task"):
    forAll: (state: AppState, key: ClientKey, at: Instant) =>
      state.earliestNonAcquired.forall: nonAcquiredTask =>
        val (newState, ot) = state.tryAcquire(key, at)
        val task           = ot.get
        state.add(task) == newState && task == nonAcquiredTask.assignTo(key, at)

  test("tryAcquire then apply = Found"):
    forAll: (state: AppState, key: ClientKey, at: Instant) =>
      val (newState, task) = state.tryAcquire(key, at)
      task.forall(t => newState.apply(t.id, key) == GetTaskResult.Found(t))

  test("acquiredBefore is acquiredBefore"):
    forAll: (state: AppState, since: Instant) =>
      state.acquiredBefore(since).forall(_.acquiredBefore(since))

  test("updateOrGiveUp is a subset of given tasks"):
    forAll: (state: AppState, candidates: List[Work.Task]) =>
      val (_, givenUp) = state.updateOrGiveUp(candidates)
      givenUp.toSet.subsetOf(candidates.toSet)

  test("updateOrGiveUp preserves size"):
    forAll: (state: AppState, before: Instant) =>
      val candidates          = state.acquiredBefore(before)
      val (newState, givenUp) = state.updateOrGiveUp(candidates)
      newState.size + givenUp.size == state.size

  test("all given up tasks are outOfTries"):
    forAll: (state: AppState, before: Instant) =>
      val candidates   = state.acquiredBefore(before)
      val (_, givenUp) = state.updateOrGiveUp(candidates)
      givenUp.forall(_.isOutOfTries)

  test("all candidates that are not given up are not outOfTries"):
    forAll: (state: AppState, before: Instant) =>
      val candidates   = state.acquiredBefore(before)
      val (_, givenUp) = state.updateOrGiveUp(candidates)
      val rest         = candidates.filterNot(givenUp.contains)
      rest.forall(!_.isOutOfTries)
