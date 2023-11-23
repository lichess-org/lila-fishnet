package lila.fishnet

import org.scalacheck.{ Arbitrary, Gen }
import org.scalacheck.Arbitrary.*
import java.time.Instant
import Work.Acquired

object Arbitraries:

  given Arbitrary[WorkId]    = Arbitrary(Gen.stringOfN(8, Gen.alphaNumChar).map(WorkId.apply))
  given Arbitrary[ClientKey] = Arbitrary(Gen.uuid.map(_.toString).map(ClientKey.apply))
  given Arbitrary[Instant]   = Arbitrary(Gen.choose(0, 300).map(Instant.now.minusSeconds(_)))

  given Arbitrary[Acquired] = Arbitrary:
    for
      key <- arbitrary[ClientKey]
      at  <- arbitrary[Instant]
    yield Acquired(key, at)
