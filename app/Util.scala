package lila.fishnet

import java.lang.Double.parseDouble
import java.lang.Integer.parseInt
import scala.util.Try

object Util {
  def parseIntOption(str: String)    = Try(parseInt(str)).toOption
  def parseDoubleOption(str: String) = Try(parseDouble(str)).toOption
  def nowMillis: Long                = System.currentTimeMillis()
}
