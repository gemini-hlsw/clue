package clue.model

import io.circe.Json
import io.circe.generic.JsonCodec
import io.circe.generic.extras._

@ConfiguredJsonCodec
sealed trait StreamingMessage

object StreamingMessage {
  sealed trait SubscriptionMessage extends StreamingMessage {
    val id: String
  }

  sealed trait PayloadMessage[P] extends StreamingMessage {
    val payload: P
  }

  final case class ConnectionInit(payload: Map[String, String] = Map.empty)
      extends PayloadMessage[Map[String, String]]

  final case object ConnectionAck extends StreamingMessage

  final case class ConnectionError(payload: Json) extends PayloadMessage[Json]

  final case object ConnectionKeepAlive extends StreamingMessage

  final case class Start(id: String, payload: GraphQLRequest)
      extends SubscriptionMessage
      with PayloadMessage[GraphQLRequest]

  final case class Stop(id: String) extends SubscriptionMessage

  final case object ConnectionTerminate extends StreamingMessage

  @JsonCodec
  final case class DataWrapper(data: Json)

  final case class Data(id: String, payload: DataWrapper)
      extends SubscriptionMessage
      with PayloadMessage[DataWrapper]

  final object DataJson {
    def unapply(data: Data): Option[(String, Json)] = Some((data.id, data.payload.data))
  }

  final case class Error(id: String, payload: Json)
      extends SubscriptionMessage
      with PayloadMessage[Json]

  final case class Complete(id: String) extends SubscriptionMessage
}
