package controllers

import javax.inject._
import play.api.Configuration
import play.api.http.HeaderNames
import play.api.libs.json._
import play.api.mvc._
import scala.concurrent.{ Future, ExecutionContext }

import lila.fishnet._

@Singleton
class FishnetController @Inject() (
    config: Configuration,
    lila: Lila,
    moveDb: MoveDb,
    val controllerComponents: ControllerComponents
)(implicit ec: ExecutionContext) extends BaseController {

  import JsonApi.readers._
  import JsonApi.writers._

  val sendMove = lila.pubsub("fishnet-in", "fishnet-out")

  def acquire = ClientAction[JsonApi.Request.Acquire] { req =>
    doAcquire(req)
  }

  def move(workId: String) = ClientAction[JsonApi.Request.PostMove] { data =>
    moveDb.postResult(Work.Id(workId), data) flatMap { move =>
      move foreach sendMove
      doAcquire(data)
    }
  }

  private def doAcquire(req: JsonApi.Request): Future[Option[JsonApi.Work]] =
    moveDb.acquire(req.clientKey) map { _ map JsonApi.moveFromWork }

  private def ClientAction[A <: JsonApi.Request](f: A => Future[Option[JsonApi.Work]])(implicit reads: Reads[A]) =
    Action.async(parse.tolerantJson) { req =>
      req.body.validate[A].fold(
        err => Future successful BadRequest(JsError toJson err),
        data => f(data).map {
          case Some(work) => Accepted(Json toJson work)
          case None => NoContent
        }
      )
    }
}
