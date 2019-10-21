package lila.fishnet

import javax.inject._
import org.joda.time.DateTime
import play.api.Configuration
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.{ Cursor, DefaultDB, MongoConnection, MongoDriver, ReadConcern }
import reactivemongo.bson._
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
final class Mongo @Inject() (config: Configuration)(implicit ec: ExecutionContext) {

  private val uri = config.get[String]("mongo.uri")

  private val driver = MongoDriver()
  private val parsedUri = MongoConnection.parseURI(uri)
  private val connection = Future.fromTry(parsedUri.flatMap(driver.connection(_, true)))

  private def db: Future[DefaultDB] = connection.flatMap(_.database("lichess"))
  private def collNamed(name: String) = db.map(_.collection(name))

  def clientColl = collNamed("fishnet_client")
}

object Mongo {

  implicit val BSONDateTimeHandler: BSONHandler[BSONDateTime, DateTime] =
    new BSONHandler[BSONDateTime, DateTime] {
      def read(time: BSONDateTime) = new DateTime(time.value)
      def write(jdtime: DateTime) = BSONDateTime(jdtime.getMillis)
    }
}

