package lila.fishnet

import weaver.*

object TaskCodecTest extends SimpleIOSuite:

  val json =
    """{"id":"KPMMo99D","request":{"id":"xF0R6bn2","initialFen":"rnbqkbnr\/pppppppp\/8\/8\/8\/8\/PPPPPPPP\/RNBQKBNR w KQkq - 0 1","variant":"Standard","moves":"e2e4 e7e6 d2d4 d7d5 e4e5 c7c5 d4c5 b8c6 c1e3 d5d4 e3f4 f8c5 f1b5 g8e7","level":2,"clock":{"wtime":29880,"btime":31380,"inc":3}},"tries":0,"acquired":null,"createdAt":"2024-03-09T14:21:02.665278Z"}"""

  test("anti regression: serialize and deserialize a task"):
    fs2.Stream
      .emits(json.getBytes)
      .through(TasksSerDe.deserialize)
      .through(TasksSerDe.serialize)
      .compile
      .foldMonoid
      .map(expect.same(_, json))
