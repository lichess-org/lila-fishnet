package lila.fishnet

import weaver.*
import weaver.scalacheck.Checkers
import cats.effect.IO
import cats.effect.kernel.Ref
import org.joda.time.DateTime
import monocle.syntax.all.*

object ExecutorTest extends SimpleIOSuite with Checkers:

  val workId = Work.Id("id")
  val work = Work.Move(
    _id = workId,
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

  test("acqurired => none"):
    for
      executor <- createExecutor()
      acquired <- executor.acquire(MoveDb.Acquire(ClientKey("key")))
    yield assert(acquired.isEmpty)

  test("add => acqurired => work.some"):
    for
      executor       <- createExecutor()
      _              <- executor.add(work)
      acquiredOption <- executor.acquire(MoveDb.Acquire(ClientKey("key")))
      acquired = acquiredOption.get
    yield assert(acquired.acquired.get.clientKey == key) `and` assert(acquired.copy(acquired = None) == work)

  test("add => acqurired => acquired => none"):
    for
      executor <- createExecutor()
      _        <- executor.add(work)
      _        <- executor.acquire(MoveDb.Acquire(ClientKey("key")))
      acquired <- executor.acquire(MoveDb.Acquire(ClientKey("key")))
    yield assert(acquired.isEmpty)

  test("add => acqurired => move => done"):
    for
      ref <- Ref.of[IO, List[Lila.Move]](Nil)
      client = createLilaClient(ref)
      executor <- Executor.instance(client)
      _        <- executor.add(work)
      _        <- executor.acquire(MoveDb.Acquire(ClientKey("key")))
      _        <- executor.move(workId, Fishnet.PostMove(Fishnet.Fishnet(key), Fishnet.MoveResult("e2e4")))
      move     <- ref.get.map(_.head)
    yield assert(move == Lila.Move(work.game, chess.format.Uci.Move("e2e4").get))

  test("add => acqurired => post invalid move => no send"):
    for
      ref <- Ref.of[IO, List[Lila.Move]](Nil)
      client = createLilaClient(ref)
      executor <- Executor.instance(client)
      _        <- executor.add(work)
      _        <- executor.acquire(MoveDb.Acquire(ClientKey("key")))
      _        <- executor.move(workId, Fishnet.PostMove(Fishnet.Fishnet(key), Fishnet.MoveResult("ee4")))
      moves    <- ref.get
    yield assert(moves.isEmpty)

  test("add => acqurired => post invalid move => acquired => work.some"):
    for
      ref <- Ref.of[IO, List[Lila.Move]](Nil)
      client = createLilaClient(ref)
      executor       <- Executor.instance(client)
      _              <- executor.add(work)
      _              <- executor.acquire(MoveDb.Acquire(ClientKey("key")))
      _              <- executor.move(workId, Fishnet.PostMove(Fishnet.Fishnet(key), Fishnet.MoveResult("ee4")))
      acquiredOption <- executor.acquire(MoveDb.Acquire(ClientKey("key")))
      acquired = acquiredOption.get
    yield assert(acquired.acquired.get.clientKey == key) `and` assert(acquired.tries == 1) `and` assert(
      acquired.copy(acquired = None, tries = 0) == work
    )

  def createExecutor(): IO[Executor] =
    createLilaClient.flatMap(Executor.instance)

  def createLilaClient: IO[LilaClient] =
    Ref.of[IO, List[Lila.Move]](Nil)
      .map(createLilaClient)

  def createLilaClient(ref: Ref[IO, List[Lila.Move]]): LilaClient =
    new LilaClient:
      def send(move: Lila.Move): IO[Unit] =
        ref.update(_ :+ move)
