package lila.fishnet

import cats.collections.Heap
import munit.ScalaCheckSuite
import org.scalacheck.Prop.{ forAll, propBoolean }
import java.time.Instant
import org.scalacheck.Arbitrary.*

import State.*
import Arbitraries.given

class StateImplTest extends ScalaCheckSuite:

  test("acquire when there is no work should return none"):
    forAll: (state: StateImpl[Int], id: WorkId, key: ClientKey) =>
      val newState = state.copy(queue = Heap.empty, failed = Heap.empty)
      newState.acquire(key).isEmpty

  test("when there is no failed work acquire should return the latest work"):
    forAll: (state: StateImpl[Int], request: Int, id: WorkId, key: ClientKey) =>
      val newState = state.copy(failed = Heap.empty).add(request, id, Instant.now)
      assertEquals(newState.acquire(key).get._1, request)
