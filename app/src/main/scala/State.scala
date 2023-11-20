package lila.fishnet

import java.time.Instant

import cats.syntax.all.*
import State.*
import cats.collections.Heap
import cats.kernel.Order

// refactor heap to normal queue because we want to get the oldest request
trait State[A]:
  def add(request: A, id: WorkId, at: Instant): State[A]
  def acquire(key: ClientKey): Option[(A, State[A])]
  def pop(key: AcquiredKey): Option[State[A]]
  def fail(key: AcquiredKey): Option[State[A]]
  def clean(before: Instant): State[A]

  // ???
  def size(): Int = ???

case class StateImpl[A](
    queue: Heap[Request[A]],
    failed: Heap[AcquiredRequest[A]],
    acquired: Map[AcquiredKey, AcquiredRequest[A]],
) extends State[A]:

  def add(request: A, id: WorkId, at: Instant): State[A] =
    copy(queue = queue.add(State.Request(request, id, at)))

  def acquire(key: ClientKey): Option[(A, State[A])] =
    failed.pop.filter((r, _) => r.canAcquired(key))
      .map((r, h) => (r.request.request, copy(failed = h)))
      .orElse(queue.pop.map((r, h) => (r.request, copy(queue = h))))

  def pop(key: AcquiredKey): Option[State[A]] =
    acquired.get(key).map: job =>
      copy(acquired = acquired - key)

  def fail(key: AcquiredKey): Option[State[A]] =
    acquired.get(key).map: job =>
      val newState = copy(acquired = acquired - key)
      job.updateOrGiveUp().fold(newState): updatedJob =>
        newState.copy(failed = failed.add(updatedJob))

  def clean(before: Instant): State[A] =
    val newAcqruied = acquired.flatMap: (k, v) =>
      (if v.acquired.at.isBefore(before) then v.updateOrGiveUp() else v.some).map(k -> _)
    copy(acquired = newAcqruied)

object State:

  def empty[A]: State[A] = StateImpl(Heap.empty, Heap.empty, Map.empty)

  given [A]: Order[Request[A]] with
    def compare(x: Request[A], y: Request[A]): Int =
      val compareCreatedAt = y.createdAt.compareTo(x.createdAt)
      if compareCreatedAt != 0 then compareCreatedAt
      else x.id.value.compareTo(y.id.value)

  given [A]: Order[AcquiredRequest[A]] with
    def compare(x: AcquiredRequest[A], y: AcquiredRequest[A]): Int =
      val compareCreatedAt = y.request.createdAt.compareTo(x.request.createdAt)
      if compareCreatedAt != 0 then compareCreatedAt
      else x.request.id.value.compareTo(y.request.id.value)

  case class AcquiredKey(key: ClientKey, id: WorkId)
  case class Acquired(clientKey: ClientKey, at: Instant)
  case class Request[A](request: A, id: WorkId, createdAt: Instant)
  case class AcquiredRequest[A](request: Request[A], acquired: Acquired, tries: Int = 0):
    def canAcquired(key: ClientKey): Boolean = acquired.clientKey != key
    def isAcquired(workId: WorkId): Boolean  = request.id == workId
    def key: AcquiredKey                     = AcquiredKey(acquired.clientKey, request.id)

    def updateOrGiveUp(): Option[AcquiredRequest[A]] =
      if tries >= 3 then None
      else copy(tries = tries + 1).some
