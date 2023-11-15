package lila.fishnet

import weaver.*
import weaver.scalacheck.Checkers
import cats.effect.IO
import cats.effect.kernel.Ref
import org.joda.time.DateTime
import monocle.syntax.all.*

object ExecutorTest extends SimpleIOSuite with Checkers:

  val work = Work.Move(
    _id = Work.Id("id"),
    game = Work.Game(
      id = "id",
      initialFen = None,
      variant = chess.variant.Standard,
      moves = "",
    ),
    level = 1,
    clock = None,
    tries = 0,
    lastTryByKey = None,
    acquired = None,
    createdAt = DateTime.now,
  )

  val key = ClientKey("key")

  test("add => acqurired => work"):
    for
      executor       <- createExecutor()
      _              <- executor.add(work)
      acquiredOption <- executor.acquire(MoveDb.Acquire(ClientKey("key")))
      acquired = acquiredOption.get
    yield assert(acquired.acquired.get.clientKey == key) `and` assert(acquired.copy(acquired = None) == work)

  def createExecutor(): IO[Executor] =
    createLilaClient.flatMap(Executor.instance)

  def createLilaClient: IO[LilaClient] =
    Ref.of[IO, List[Lila.Move]](Nil)
      .map: ref =>
        new LilaClient:
          def send(move: Lila.Move): IO[Unit] =
            ref.update(_ :+ move)
