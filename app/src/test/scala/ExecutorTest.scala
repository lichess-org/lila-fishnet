package lila.fishnet

import cats.effect.{ IO, Ref }
import cats.syntax.all.*
import chess.format.Uci
import chess.macros.uci
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.noop.NoOpFactory
import weaver.*

import java.time.Instant

import Helper.*

object ExecutorTest extends SimpleIOSuite:

  given LoggerFactory[IO] = NoOpFactory[IO]

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

  val validMove = BestMove(uci"e2e4")

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
      ref      <- emptyMovesRef
      executor <- createExecutor(ref)(ExecutorConfig(2))
      _        <- executor.add(request)
      acquired <- executor.acquire(key)
      _        <- executor.move(acquired.get.id, key, validMove.some)
      response <- ref.get.map(_.head)
    yield expect.same(response, Lila.Response(request.id, request.moves, uci"e2e4"))

  test("post move after timeout should not send move"):
    for
      ref      <- emptyMovesRef
      executor <- createExecutor(ref)(ExecutorConfig(2))
      _        <- executor.add(request)
      acquired <- executor.acquire(key)
      _        <- executor.clean(Instant.now.plusSeconds(37))
      _        <- executor.move(acquired.get.id, key, validMove.some)
      moves    <- ref.get
    yield assert(moves.isEmpty)

  test("after timeout move should be able to acquired again"):
    for
      ref      <- emptyMovesRef
      executor <- createExecutor(ref)(ExecutorConfig(2))
      _        <- executor.add(request)
      _        <- executor.acquire(key)
      _        <- executor.clean(Instant.now.plusSeconds(37))
      acquired <- executor.acquire(key)
      _        <- executor.move(acquired.get.id, key, validMove.some)
      response <- ref.get.map(_.head)
    yield expect.same(response, Lila.Response(request.id, request.moves, uci"e2e4"))

  test("post null move should remove the task"):
    for
      ref      <- emptyMovesRef
      executor <- createExecutor(ref)(ExecutorConfig(2))
      _        <- executor.add(request)
      acquired <- executor.acquire(key)
      _        <- executor.move(acquired.get.id, key, none)
      response <- ref.get.map(_.headOption)
      acquired <- executor.acquire(key)
    yield assert(acquired.isEmpty && response.isEmpty)

  test("should not give up after 2 tries"):
    for
      executor <- createExecutor(ExecutorConfig(2))
      _        <- executor.add(request)
      _ <- (executor.acquire(key).flatMap(_ => executor.clean(Instant.now.plusSeconds(17)))).replicateA_(2)
      acquired <- executor.acquire(key)
    yield assert(acquired.isDefined)

  test("should give up after 3 tries"):
    for
      executor <- createExecutor(ExecutorConfig(2))
      _        <- executor.add(request)
      _ <- (executor.acquire(key).flatMap(_ => executor.clean(Instant.now.plusSeconds(17)))).replicateA_(3)
      acquired <- executor.acquire(key)
    yield assert(acquired.isEmpty)

  test("give up due to cleaning should reduce task's size"):
    for
      executor <- createExecutor(ExecutorConfig(2))
      _        <- executor.add(request)
      _        <- executor.add(request.copy(id = GameId("2")))
      _  <- (executor.acquire(key).flatMap(_ => executor.clean(Instant.now.plusSeconds(17)))).replicateA_(2)
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

  def createExecutor(ref: Ref[IO, List[Lila.Response]])(config: ExecutorConfig): IO[Executor] =
    ioExecutor(createLilaClient(ref))(noopMonitor, config)

  def ioExecutor(client: LilaClient)(monitor: Monitor, config: ExecutorConfig): IO[Executor] =
    Ref
      .of[IO, AppState](AppState.empty)
      .map(Executor.instance(client, monitor, config))

  def createLilaClient: IO[LilaClient] =
    emptyMovesRef.map(createLilaClient)

  def createLilaClient(ref: Ref[IO, List[Lila.Response]]): LilaClient = new:
    def send(move: Lila.Response): IO[Unit] =
      ref.update(_ :+ move)

  def emptyMovesRef: IO[Ref[IO, List[Lila.Response]]] =
    Ref.of[IO, List[Lila.Response]](Nil)
