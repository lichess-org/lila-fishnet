package lila.app

import play.api._
import play.api.routing.{ Router, SimpleRouter }
import play.api.routing.sird._
import akka.actor.ActorSystem

final class AppLoader extends ApplicationLoader {
  def load(ctx: ApplicationLoader.Context): Application = new LilaComponents(ctx).application
}

final class LilaComponents(ctx: ApplicationLoader.Context) extends BuiltInComponentsFromContext(ctx) {

  LoggerConfigurator(ctx.environment.classLoader).foreach {
    _.configure(ctx.environment, ctx.initialConfiguration, Map.empty)
  }

  println {
    val java = System.getProperty("java.version")
    val mem  = Runtime.getRuntime().maxMemory() / 1024 / 1024
    s"lila-fishnet ${ctx.environment.mode} / java ${java}, memory: ${mem}MB"
  }

  override val httpFilters = Seq.empty

  import _root_.controllers._

  implicit def system: ActorSystem = actorSystem

  lazy val moveDb     = new lila.fishnet.MoveDb
  lazy val redis      = new lila.fishnet.Lila(moveDb, configuration)
  lazy val controller = new FishnetController(configuration, redis, moveDb, controllerComponents)

  // eagerly wire up all controllers
  val router: Router = new SimpleRouter {
    def routes: Router.Routes = {
      case POST(p"/fishnet/acquire")      => controller.acquire
      case POST(p"/fishnet/move/$workId") => controller.move(workId)
    }
  }

  if (configuration.get[Boolean]("kamon.enabled")) {
    println("Kamon is enabled")
    kamon.Kamon.loadModules()
  }
}
