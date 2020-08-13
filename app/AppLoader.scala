package lila.app

import play.api._
import scala.annotation.nowarn
import play.api.routing.Router
import router.Routes

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

  implicit def system = actorSystem

  lazy val moveDb     = new lila.fishnet.MoveDb
  lazy val redis      = new lila.fishnet.Lila(moveDb, configuration)
  lazy val controller = new FishnetController(configuration, redis, moveDb, controllerComponents)

  // eagerly wire up all controllers
  val router: Router = {
    @nowarn val prefix: String = "/"
    new Routes(httpErrorHandler, controller)
  }

  if (configuration.get[Boolean]("kamon.enabled")) {
    println("Kamon is enabled")
    kamon.Kamon.loadModules()
  }
}
