package clue

import io.circe.generic.extras._
import io.circe.Json
import io.circe.generic.JsonCodec

import io.circe._
import io.circe.generic.semiauto._

@ConfiguredJsonCodec
protected[clue] sealed trait StreamingMessage

object StreamingMessage {
  protected[clue] sealed trait SubscriptionMessage extends StreamingMessage {
    val id: String
  }

  protected[clue] sealed trait PayloadMessage[P] extends StreamingMessage {
    val payload: P
  }

  protected[clue] final case class ConnectionInit(payload: Map[String, String] = Map.empty)
      extends PayloadMessage[Map[String, String]]

  protected[clue] final case object ConnectionAck extends StreamingMessage

  protected[clue] final case class ConnectionError(payload: Json) extends PayloadMessage[Json]

  protected[clue] final case object ConnectionKeepAlive extends StreamingMessage

  protected[clue] final case class Start(id: String, payload: GraphQLRequest)
      extends SubscriptionMessage
      with PayloadMessage[GraphQLRequest]

  protected[clue] final case class Stop(id: String) extends SubscriptionMessage

  protected[clue] final case object ConnectionTerminate extends StreamingMessage

  @JsonCodec
  protected[clue] final case class DataWrapper(data: Json)

  protected[clue] final case class Data(id: String, payload: DataWrapper)
      extends SubscriptionMessage
      with PayloadMessage[DataWrapper]

  protected[clue] final object DataJson {
    def unapply(data: Data): Option[(String, Json)] = Some((data.id, data.payload.data))
  }

  protected[clue] final case class Error(id: String, payload: Json)
      extends SubscriptionMessage
      with PayloadMessage[Json]

  protected[clue] final case class Complete(id: String) extends SubscriptionMessage
}
