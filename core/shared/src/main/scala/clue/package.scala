import io.circe.generic.extras._

package object clue {
  protected[clue] type MessageType = String

  private val messageTypes: Map[String, String] = Map(
    "ConnectionInit" -> "connection_init",
    "ConnectionAck" -> "connection_ack",
    "ConnectionError" -> "connection_error",
    "ConnectionKeepAlive" -> "ka",
    "Start" -> "start",
    "Stop" -> "stop",
    "ConnectionTerminate" -> "connection_terminate",
    "Data" -> "data",
    "Error" -> "error",
    "Complete" -> "complete"
  )

  implicit protected[clue] val genDevConfig: Configuration =
    Configuration.default
      .withDiscriminator("type")
      .copy(transformConstructorNames = messageTypes)
}
