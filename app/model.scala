package lila.fishnet

trait StringValue extends Any:
  def value: String
  override def toString = value
trait IntValue extends Any:
  def value: Int
  override def toString = value.toString

case class IpAddress(value: String) extends AnyVal with StringValue

case class ClientKey(value: String) extends AnyVal with StringValue
