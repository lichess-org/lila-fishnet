package lila.fishnet

import cats.effect.testkit.TestControl
import cats.effect.{ IO, Ref }
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger
import weaver.*

import java.time.Instant
import scala.concurrent.duration.*

object CleaningJobTest extends SimpleIOSuite:

  given Logger[IO] = NoOpLogger[IO]

  val times = (60 - 5) / 3 + 1
  test(s"cleaning run $times times in 1 minute"):
    val res = for
      ref <- Ref.of[IO, Int](0).toResource
      executor = createExcutor(ref)
      _     <- WorkCleaningJob(executor).run()
      _     <- IO.sleep(1.minute).toResource
      count <- ref.get.toResource
    yield count
    TestControl.executeEmbed(res.use(count => IO(expect.same(count, times))))

  def createExcutor(ref: Ref[IO, Int]): Executor = new:
    def acquire(accquire: ClientKey)                                 = IO.none
    def move(workId: WorkId, key: ClientKey, move: Option[BestMove]) = IO.unit
    def add(work: Lila.Request)                                      = IO.unit
    def clean(before: Instant)                                       = ref.update(_ + 1)
