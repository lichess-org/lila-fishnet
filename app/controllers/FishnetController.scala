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
    mongo: Mongo,
    moveDb: MoveDb,
    val controllerComponents: ControllerComponents
)(implicit ec: ExecutionContext) extends BaseController {

  import JsonApi.readers._
  import JsonApi.writers._

  def acquire = ClientAction[JsonApi.Request.Acquire] { req =>
    moveDb.acquire(req.clientKey) map { _ map JsonApi.moveFromWork } map Right.apply
  }

  def move(workId: String) = Action { req =>
    Ok(s"move $workId")
  }

  private def ClientAction[A <: JsonApi.Request](f: A => Future[Either[Result, Option[JsonApi.Work]]])(implicit reads: Reads[A]) =
    Action.async(parse.tolerantJson) { req =>
      req.body.validate[A].fold(
        err => Future successful BadRequest(JsError toJson err),
        data => f(data).map {
          case Right(Some(work)) => Accepted(Json toJson work)
          case Right(None) => NoContent
          case Left(result) => result
        }
      )
    }
}
