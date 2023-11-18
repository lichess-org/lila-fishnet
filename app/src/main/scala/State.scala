package lila.fishnet

import java.time.Instant

import cats.syntax.all.*
import State.*
import cats.collections.Heap
import cats.kernel.Order
import chess.format.Uci

case class State[A](
    queue: Heap[Request[A]],
    failed: Heap[AcquiredRequest[A]],
    acquired: Map[AcquiredKey, AcquiredRequest[A]],
):

  def add(request: A, id: Work.Id, at: Instant): State[A] =
    copy(queue = queue.add(State.Request(request, id, at)))

  def acquire(key: ClientKey): Option[(A, State[A])] =
    failed.pop.filter((r, _) => r.canAcquired(key))
      .map((r, h) => (r.request.request, copy(failed = h)))
      .orElse(queue.pop.map((r, h) => (r.request, copy(queue = h))))

  def pop(key: AcquiredKey, bestMove: Uci): Either[MoveError, State[A]] =
    acquired.get(key).fold(MoveError.KeyNotFound(key).asLeft): job =>
      val newAcquiredMap = acquired - key
      copy(acquired = newAcquiredMap).asRight[MoveError]

  def clean(before: Instant): State[A] =
    val newAcqruied = acquired.flatMap: (k, v) =>
      (if v.acquired.at.isBefore(before) then v.updateOrGiveUp() else v.some).map(k -> _)
    copy(acquired = newAcqruied)

object State:

  def empty[A]: State[A] = State(Heap.empty, Heap.empty, Map.empty)

  enum MoveError:
    case KeyNotFound(key: AcquiredKey)
    case OutOfTries(workId: Work.Id, key: ClientKey)

  given [A]: Order[Request[A]] with
    def compare(x: Request[A], y: Request[A]): Int =
      val compareCreatedAt = x.createdAt.compareTo(y.createdAt)
      if compareCreatedAt != 0 then compareCreatedAt
      else x.id.value.compareTo(y.id.value)

  given [A]: Order[AcquiredRequest[A]] with
    def compare(x: AcquiredRequest[A], y: AcquiredRequest[A]): Int =
      val compareCreatedAt = x.request.createdAt.compareTo(y.request.createdAt)
      if compareCreatedAt != 0 then compareCreatedAt
      else x.request.id.value.compareTo(y.request.id.value)

  case class AcquiredKey(key: ClientKey, id: Work.Id)
  case class Acquired(clientKey: ClientKey, at: Instant)
  case class Request[A](request: A, id: Work.Id, createdAt: Instant)
  case class AcquiredRequest[A](request: Request[A], acquired: Acquired, tries: Int = 0):
    def canAcquired(key: ClientKey): Boolean = acquired.clientKey != key
    def isAcquired(workId: Work.Id): Boolean = request.id == workId

    def updateOrGiveUp(): Option[AcquiredRequest[A]] =
      if tries >= 3 then None
      else copy(tries = tries + 1).some
