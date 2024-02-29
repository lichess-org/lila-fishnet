package lila.fishnet

import munit.ScalaCheckSuite
import org.scalacheck.Prop.{ forAll, propBoolean }
import Arbitraries.given

class AppStateTest extends ScalaCheckSuite:
  test("request"):
    forAll: (state: AppState, task: Work.Task) =>
      state.add(task).size == 1
