package lila.fishnet

import cats.effect.IO
import cats.effect.kernel.{ Ref, Resource }
import cats.effect.testkit.TestControl
import java.time.Instant
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger
import weaver.*

import scala.concurrent.duration.*

object CleaningJobTest extends SimpleIOSuite:

  given Logger[IO] = NoOpLogger[IO]

  val times = (60 - 5) / 3 + 1
  test(s"cleaning run $times times in 1 minute"):
    val res = for
      ref <- Resource.eval(Ref.of[IO, Int](0))
      executor = createExcutor(ref)
      _     <- WorkCleaningJob(executor).run().background
      _     <- Resource.eval(IO.sleep(1.minute))
      count <- Resource.eval(ref.get)
    yield count
    TestControl.executeEmbed(res.use(count => IO(expect.same(count, times))))

  def createExcutor(ref: Ref[IO, Int]): Executor =
    new Executor:
      def acquire(accquire: ClientKey)                                = IO.none
      def move(workId: WorkId, fishnetKey: ClientKey, move: BestMove) = IO.unit
      def add(work: Lila.Request)                                     = IO.unit
      def clean(before: Instant)                                      = ref.update(_ + 1)
