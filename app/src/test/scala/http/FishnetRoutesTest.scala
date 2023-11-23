package lila.fishnet
package http

import cats.effect.IO
import cats.syntax.all.*
import io.circe.*
import io.circe.literal.*
import java.time.Instant
import org.http4s.*
import org.http4s.implicits.*
import org.http4s.circe.*
import weaver.*

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

  val requestWithId = Work.RequestWithId(
    id = WorkId("workid"),
    request = Lila.Request(
      id = GameId("gameid"),
      initialFen = chess.format.Fen.Epd("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"),
      moves = "",
      variant = chess.variant.Standard,
      level = 1,
      clock = Some(Lila.Clock(wtime = 600, btime = 600, inc = 0))
    )
  )

  test("POST /fishnet/acquire should return work response"):
    val executor = createExecutor()
    val routes   = createRoutes(executor)
    val req      = Request[IO](Method.POST, uri"/fishnet/acquire").withEntity(acqurieRequestBody)
    exepectHttpBodyAndStatus(routes, req)(expectedBody = workResponse, expectedStatus = Status.Ok)

  test("POST /fishnet/move should also return work response"):
    val executor = createExecutor()
    val routes   = createRoutes(executor)
    val req      = Request[IO](Method.POST, uri"/fishnet/move/workid").withEntity(postMoveRequestBody)
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

  def createExecutor(): Executor =
    new Executor:
      def acquire(key: ClientKey) = IO.pure(requestWithId.some)
      def move(id: WorkId, key: ClientKey, move: BestMove): IO[Unit] =
        if id == requestWithId.id then IO.unit
        else IO.raiseError(new Exception("invalid work id"))
      def add(request: Lila.Request): IO[Unit] = IO.unit
      def clean(before: Instant)               = IO.unit
