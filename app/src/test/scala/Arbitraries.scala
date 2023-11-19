package lila.fishnet

import org.scalacheck.{ Arbitrary, Gen }
import org.scalacheck.Arbitrary.*
import java.time.Instant
import State.*
import cats.collections.Heap
import cats.Order

object Arbitraries:

  given Arbitrary[Work.Id]   = Arbitrary(Gen.stringOfN(8, Gen.alphaNumChar).map(Work.Id.apply))
  given Arbitrary[ClientKey] = Arbitrary(Gen.uuid.map(_.toString).map(ClientKey.apply))
  given Arbitrary[Instant]   = Arbitrary(Gen.choose(0, 300).map(Instant.now.minusSeconds(_)))

  given Arbitrary[AcquiredKey] = Arbitrary:
    for
      key <- arbitrary[ClientKey]
      id  <- arbitrary[Work.Id]
    yield AcquiredKey(key, id)

  given Arbitrary[Acquired] = Arbitrary:
    for
      key <- arbitrary[ClientKey]
      at  <- arbitrary[Instant]
    yield Acquired(key, at)

  given [A](using Arbitrary[A]): Arbitrary[Request[A]] = Arbitrary:
    for
      request   <- arbitrary[A]
      id        <- arbitrary[Work.Id]
      createdAt <- arbitrary[Instant]
    yield Request(request, id, createdAt)

  given [A](using Arbitrary[A]): Arbitrary[AcquiredRequest[A]] = Arbitrary:
    for
      request  <- arbitrary[Request[A]]
      acquired <- arbitrary[Acquired]
      tries    <- Gen.choose(0, 3)
    yield AcquiredRequest(request, acquired, tries)

  given [A](using Arbitrary[A], Order[A]): Arbitrary[Heap[A]] =
    Arbitrary(Gen.listOf(arbitrary[A]).map(Heap.fromIterable(_)))

  given [A](using Arbitrary[A], Order[A]): Arbitrary[Map[AcquiredKey, AcquiredRequest[A]]] =
    Arbitrary(Gen.listOf(arbitrary[AcquiredRequest[A]]).map(_.map(r => r.key -> r).toMap))

  given [A](using Arbitrary[A], Order[A]): Arbitrary[StateImpl[A]] = Arbitrary:
    for
      queue    <- arbitrary[Heap[Request[A]]]
      failed   <- arbitrary[Heap[AcquiredRequest[A]]]
      acquired <- arbitrary[Map[AcquiredKey, AcquiredRequest[A]]]
    yield StateImpl(queue, failed, acquired)
