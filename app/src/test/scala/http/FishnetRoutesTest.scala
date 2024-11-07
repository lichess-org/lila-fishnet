package lila.fishnet
package http

import cats.effect.IO
import cats.syntax.all.*
import io.circe.*
import io.circe.literal.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.implicits.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger
import weaver.*

import java.time.Instant

object FishnetRoutesTest extends SimpleIOSuite:

  val acqurieRequestBody = json"""{
    "fishnet": {
      "version": "1.0.0",
      "apikey": "apikey"
    }
  }"""

  val postMoveRequestBody = json"""{
    "fishnet": {
      "version": "1.0.0",
      "apikey": "apikey"
    },
    "move": {
      "bestmove": "e2e4"
    }
  }"""

  val postNullMoveRequestBody = json"""{
    "fishnet": {
      "version": "1.0.0",
      "apikey": "apikey"
    },
    "move": {
      "bestmove": null
    }
  }"""

  val workResponse: Json = json"""{
    "work": {
      "id": "workid",
      "level": 1,
      "clock": {
        "wtime": 600,
        "btime": 600,
        "inc": 0
      },
      "type": "move"
    },
    "game_id": "gameid",
    "position": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR",
    "moves": "",
    "variant": "Standard"
  }"""

  given Logger[IO] = NoOpLogger[IO]

  val task = Work.Task(
    id = WorkId("workid"),
    request = Lila.Request(
      id = GameId("gameid"),
      initialFen = chess.format.Fen.Full("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"),
      moves = "",
      variant = chess.variant.Standard,
      level = 1,
      clock = Some(Lila.Clock(wtime = 600, btime = 600, inc = 0))
    ),
    tries = 1,
    acquired = none,
    createdAt = Instant.now
  )

  test("POST /fishnet/acquire should return work response"):
    val executor = createExecutor()
    val routes   = createRoutes(executor)
    val req      = Request[IO](Method.POST, uri"/fishnet/acquire").withEntity(acqurieRequestBody)
    exepectHttpBodyAndStatus(routes, req)(expectedBody = workResponse, expectedStatus = Status.Ok)

  test("POST /fishnet/move should return work response"):
    val executor = createExecutor()
    val routes   = createRoutes(executor)
    val req      = Request[IO](Method.POST, uri"/fishnet/move/workid").withEntity(postMoveRequestBody)
    exepectHttpBodyAndStatus(routes, req)(expectedBody = workResponse, expectedStatus = Status.Ok)

  test("POST /fishnet/move with null move should also return work response"):
    val executor = createExecutor()
    val routes   = createRoutes(executor)
    val req      = Request[IO](Method.POST, uri"/fishnet/move/workid").withEntity(postNullMoveRequestBody)
    exepectHttpBodyAndStatus(routes, req)(expectedBody = workResponse, expectedStatus = Status.Ok)

  def exepectHttpBodyAndStatus(routes: HttpRoutes[IO], req: Request[IO])(
      expectedBody: Json,
      expectedStatus: Status
  ) =
    routes
      .run(req)
      .value
      .flatMap:
        case Some(resp) =>
          resp.asJson.map:
            expect.same(_, expectedBody) `and` expect.same(resp.status, expectedStatus)
        case _ => IO.pure(failure("expected response but not found"))

  def createRoutes(executor: Executor): HttpRoutes[IO] =
    FishnetRoutes(executor).routes

  def createExecutor(): Executor = new:
    def acquire(key: ClientKey) = IO.pure(task.some)
    def move(id: WorkId, key: ClientKey, move: Option[BestMove]) =
      if id == task.id then IO.unit
      else IO.raiseError(new Exception("invalid work id"))
    def add(request: Lila.Request) = IO.unit
    def clean(before: Instant)     = IO.unit
