package lila.fishnet

import cats.effect.IO
import cats.effect.kernel.Resource
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.*

trait WorkCleaningJob:
  def run(): Resource[IO, Unit]

object WorkCleaningJob:
  def apply(executor: Executor)(using Logger[IO]): WorkCleaningJob = new:
    def run(): Resource[IO, Unit] =
      (Logger[IO].info("Start cleaning job") *>
        IO.sleep(5.seconds) *>
        (IO.realTimeInstant.flatMap(now => executor.clean(now.minusSeconds(3))) *>
          IO.sleep(3.seconds)).foreverM).background.map(_ => ())
