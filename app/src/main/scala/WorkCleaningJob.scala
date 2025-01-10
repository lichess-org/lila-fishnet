package lila.fishnet

import cats.effect.{IO, Resource, Deferred}
import cats.syntax.all.*
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.*

trait WorkCleaningJob:
  def run(): Resource[IO, Unit]

object WorkCleaningJob:
  def apply(executor: Executor, cleanInterval: FiniteDuration = 3.seconds, initialDelay: FiniteDuration = 5.seconds)(using Logger[IO]): WorkCleaningJob = new:
    def run(): Resource[IO, Unit] =
      for
        _ <- Resource.make(Logger[IO].info("Starting cleaning job").attempt.void) { _ =>
          Logger[IO].info("Cleaning job has been stopped").attempt.void
        }
        stopSignal <- Resource.make(Deferred[IO, Unit]) { _ =>
          Logger[IO].info("Stopping cleaning job").attempt.void
        }
        _ <- Resource.eval(runCleaningJob(stopSignal, cleanInterval, initialDelay))
      yield ()

    private def runCleaningJob(stopSignal: Deferred[IO, Unit], cleanInterval: FiniteDuration, initialDelay: FiniteDuration)(using Logger[IO]): IO[Unit] =
      (for
        _ <- IO.sleep(initialDelay)
        _ <- Logger[IO].info("Cleaning job started")
        _ <- (IO.realTimeInstant.flatMap(now => executor.clean(now.minusSeconds(cleanInterval.toSeconds))) *>
              IO.sleep(cleanInterval)).foreverM
      ).guaranteeCase {
        case ExitCase.Completed => Logger[IO].info("Cleaning job completed successfully")
        case ExitCase.Error(e) => Logger[IO].error(e)("Cleaning job encountered an error")
        case ExitCase.Canceled => Logger[IO].info("Cleaning job was canceled")
      }.start.flatMap { fiber =>
        stopSignal.get.flatMap(_ => fiber.cancel).handleErrorWith { e =>
          Logger[IO].error(e)("Error while stopping the cleaning job")
        }
      }   
