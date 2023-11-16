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

  val acquiredKey = MoveDb.Acquire(key)

  val validMove   = Fishnet.PostMove(Fishnet.Fishnet(key), Fishnet.MoveResult("e2e4"))
  val invalidMove = Fishnet.PostMove(Fishnet.Fishnet(key), Fishnet.MoveResult("ee4"))

  test("acquire when there is no work should return none"):
    for
      executor <- createExecutor()
      acquired <- executor.acquire(acquiredKey)
    yield assert(acquired.isEmpty)

  test("acquire when there is work should return work.some"):
    for
      executor       <- createExecutor()
      _              <- executor.add(work)
      acquiredOption <- executor.acquire(acquiredKey)
      acquired = acquiredOption.get
    yield assert(acquired.acquired.get.clientKey == key) `and` assert(acquired.copy(acquired = None) == work)

  test("after acquire the only work, acquire again should return none"):
    for
      executor <- createExecutor()
      _        <- executor.add(work)
      _        <- executor.acquire(acquiredKey)
      acquired <- executor.acquire(acquiredKey)
    yield assert(acquired.isEmpty)

  test("post move after acquire should send move"):
    for
      ref <- Ref.of[IO, List[Lila.Move]](Nil)
      client = createLilaClient(ref)
      executor <- Executor.instance(client)
      _        <- executor.add(work)
      _        <- executor.acquire(acquiredKey)
      _        <- executor.move(workId, validMove)
      move     <- ref.get.map(_.head)
    yield assert(move == Lila.Move(work.game, chess.format.Uci.Move("e2e4").get))

  test("post an invalid move should not send move"):
    for
      ref <- Ref.of[IO, List[Lila.Move]](Nil)
      client = createLilaClient(ref)
      executor <- Executor.instance(client)
      _        <- executor.add(work)
      _        <- executor.acquire(acquiredKey)
      _        <- executor.move(workId, invalidMove)
      moves    <- ref.get
    yield assert(moves.isEmpty)

  test("after post an invalid move, acquire again should return work.some"):
    for
      ref <- Ref.of[IO, List[Lila.Move]](Nil)
      client = createLilaClient(ref)
      executor       <- Executor.instance(client)
      _              <- executor.add(work)
      _              <- executor.acquire(acquiredKey)
      _              <- executor.move(workId, invalidMove)
      acquiredOption <- executor.acquire(acquiredKey)
      acquired = acquiredOption.get
    yield assert(acquired.acquired.get.clientKey == key) `and` assert(acquired.tries == 1) `and` assert(
      acquired.copy(acquired = None, tries = 0) == work
    )

  test("should not give up after 3 tries"):
    for
      ref <- Ref.of[IO, List[Lila.Move]](Nil)
      client = createLilaClient(ref)
      executor <- Executor.instance(client)
      _        <- executor.add(work)
      _        <- (executor.acquire(acquiredKey) >> executor.move(workId, invalidMove)).replicateA_(3)
      acquired <- executor.acquire(acquiredKey)
    yield assert(acquired.isDefined)

  test("should give up after 4 tries"):
    for
      ref <- Ref.of[IO, List[Lila.Move]](Nil)
      client = createLilaClient(ref)
      executor <- Executor.instance(client)
      _        <- executor.add(work)
      _        <- (executor.acquire(acquiredKey) >> executor.move(workId, invalidMove)).replicateA_(4)
      acquired <- executor.acquire(acquiredKey)
    yield assert(acquired.isEmpty)

  def createExecutor(): IO[Executor] =
    createLilaClient.flatMap(Executor.instance)

  def createLilaClient: IO[LilaClient] =
    Ref.of[IO, List[Lila.Move]](Nil)
      .map(createLilaClient)

  def createLilaClient(ref: Ref[IO, List[Lila.Move]]): LilaClient =
    new LilaClient:
      def send(move: Lila.Move): IO[Unit] =
        ref.update(_ :+ move)
