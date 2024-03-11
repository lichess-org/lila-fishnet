package lila.fishnet

import cats.effect.{ IO, Ref }
import cats.syntax.all.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger
import weaver.*

import java.time.Instant

import Helper.*

object ExecutorTest extends SimpleIOSuite:

  given Logger[IO] = NoOpLogger[IO]

  val request: Lila.Request = Lila.Request(
    id = GameId("1"),
    initialFen = chess.variant.Standard.initialFen,
    variant = chess.variant.Standard,
    moves = "",
    level = 1,
    clock = None
  )

  val key  = ClientKey("key")
  val key2 = ClientKey("key2")

  val validMove   = BestMove("e2e4")
  val invalidMove = BestMove("2e4")

  test("acquire when there is no work should return none"):
    for
      executor <- createExecutor()
      acquired <- executor.acquire(key)
    yield assert(acquired.isEmpty)

  test("acquire when there is work should return some work"):
    for
      executor       <- createExecutor()
      _              <- executor.add(request)
      acquiredOption <- executor.acquire(key)
      acquired = acquiredOption.get
    yield expect.same(acquired.request, request)

  test("acquire should return work in order"):
    val requests = List(request, request.copy(id = GameId("2")), request.copy(id = GameId("3")))
    for
      executor  <- createExecutor()
      _         <- requests.traverse(executor.add)
      acquireds <- executor.acquire(key).replicateA(3)
      ids = acquireds.map(_.get.request.id).mkString("")
    yield expect.same(ids, "123")

  test("after acquire the only work, acquire again should return none"):
    for
      executor <- createExecutor()
      _        <- executor.add(request)
      _        <- executor.acquire(key)
      acquired <- executor.acquire(key2)
    yield assert(acquired.isEmpty)

  test("post move after acquire should send move"):
    for
      ref      <- Ref.of[IO, List[Lila.Move]](Nil)
      executor <- createExecutor(ref)(ExecutorConfig(2))
      _        <- executor.add(request)
      acquired <- executor.acquire(key)
      _        <- executor.move(acquired.get.id, key, validMove)
      move     <- ref.get.map(_.head)
    yield expect.same(move, Lila.Move(request.id, request.moves, chess.format.Uci.Move("e2e4").get))

  test("post move after timeout should not send move"):
    for
      ref      <- Ref.of[IO, List[Lila.Move]](Nil)
      executor <- createExecutor(ref)(ExecutorConfig(2))
      _        <- executor.add(request)
      acquired <- executor.acquire(key)
      _        <- executor.clean(Instant.now.plusSeconds(37))
      _        <- executor.move(acquired.get.id, key, validMove)
      moves    <- ref.get
    yield assert(moves.isEmpty)

  test("after timeout move should be able to acquired again"):
    for
      ref      <- Ref.of[IO, List[Lila.Move]](Nil)
      executor <- createExecutor(ref)(ExecutorConfig(2))
      _        <- executor.add(request)
      _        <- executor.acquire(key)
      _        <- executor.clean(Instant.now.plusSeconds(37))
      acquired <- executor.acquire(key)
      _        <- executor.move(acquired.get.id, key, validMove)
      move     <- ref.get.map(_.head)
    yield expect.same(move, Lila.Move(request.id, request.moves, chess.format.Uci.Move("e2e4").get))

  test("post an invalid move should not send move"):
    for
      ref      <- Ref.of[IO, List[Lila.Move]](Nil)
      executor <- createExecutor(ref)(ExecutorConfig(2))
      _        <- executor.add(request)
      acquired <- executor.acquire(key)
      _        <- executor.move(acquired.get.id, key, invalidMove)
      moves    <- ref.get
    yield assert(moves.isEmpty)

  test("after post an invalid move, acquire again should return work.some"):
    for
      executor <- createExecutor(ExecutorConfig(2))
      _        <- executor.add(request)
      acquired <- executor.acquire(key)
      workId = acquired.get.id
      _              <- executor.move(workId, key, invalidMove)
      acquiredOption <- executor.acquire(key)
      acquired = acquiredOption.get
    yield expect.same(acquired.request, request)

  test("should not give up after 2 tries"):
    for
      executor <- createExecutor(ExecutorConfig(2))
      _        <- executor.add(request)
      _ <- (executor.acquire(key).flatMap(x => executor.move(x.get.id, key, invalidMove))).replicateA_(2)
      acquired <- executor.acquire(key)
    yield assert(acquired.isDefined)

  test("should give up after 3 tries"):
    for
      executor <- createExecutor(ExecutorConfig(2))
      _        <- executor.add(request)
      _ <- (executor.acquire(key).flatMap(x => executor.move(x.get.id, key, invalidMove))).replicateA_(3)
      acquired <- executor.acquire(key)
    yield assert(acquired.isEmpty)

  test("give up due to invalid move should reduce task's size"):
    for
      executor <- createExecutor(ExecutorConfig(2))
      _        <- executor.add(request)
      _        <- executor.add(request.copy(id = GameId("2")))
      _  <- (executor.acquire(key).flatMap(x => executor.move(x.get.id, key, invalidMove))).replicateA_(3)
      _  <- executor.add(request.copy(id = GameId("3")))
      a1 <- executor.acquire(key)
      a2 <- executor.acquire(key2)
    yield assert(a1.isDefined && a2.isDefined)

  test("give up due to cleaning should reduce task's size"):
    for
      executor <- createExecutor(ExecutorConfig(2))
      _        <- executor.add(request)
      _        <- executor.add(request.copy(id = GameId("2")))
      _  <- (executor.acquire(key).flatMap(x => executor.move(x.get.id, key, invalidMove))).replicateA_(2)
      _  <- executor.acquire(key)
      _  <- executor.clean(Instant.now.plusSeconds(37))
      _  <- executor.add(request.copy(id = GameId("3")))
      a1 <- executor.acquire(key)
      a2 <- executor.acquire(key2)
    yield assert(a1.isDefined && a2.isDefined)

  test("if moves reach max size it should clear all moves"):
    for
      executor <- createExecutor(ExecutorConfig(3))
      _        <- executor.add(request)
      _        <- executor.add(request.copy(id = GameId("2")))
      _        <- executor.add(request.copy(id = GameId("3")))
      _        <- executor.add(request.copy(id = GameId("4")))
      acquired <- executor.acquire(key)
      empty    <- executor.acquire(key2)
    yield assert(acquired.isDefined && empty.isEmpty)

  def createExecutor(config: ExecutorConfig = ExecutorConfig(300)): IO[Executor] =
    createLilaClient.flatMap(ioExecutor(_)(noopMonitor, config))

  def createExecutor(ref: Ref[IO, List[Lila.Move]])(config: ExecutorConfig): IO[Executor] =
    ioExecutor(createLilaClient(ref))(noopMonitor, config)

  def ioExecutor(client: LilaClient)(monitor: Monitor, config: ExecutorConfig): IO[Executor] =
    Ref
      .of[IO, AppState](AppState.empty)
      .map(Executor.instance(_, client, monitor, config))

  def createLilaClient: IO[LilaClient] =
    Ref
      .of[IO, List[Lila.Move]](Nil)
      .map(createLilaClient)

  def createLilaClient(ref: Ref[IO, List[Lila.Move]]): LilaClient =
    new LilaClient:
      def send(move: Lila.Move): IO[Unit] =
        ref.update(_ :+ move)
