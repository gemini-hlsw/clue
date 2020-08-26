package clue.model

import cats.Eq
import cats.implicits._

import io.circe.Json

/**
 * GraphQL web socket protocol streaming messages.  Messages are cleanly
 * divided in those coming `FromClient` and those coming `FromServer`.  See
 * also https://github.com/apollographql/subscriptions-transport-ws/blob/master/PROTOCOL.md
 */
object StreamingMessage {

  /**
   * Messages implement `Identifier` to distinguish (potentially) long-running
   * operations like subscriptions where results come in over time.
   */
  sealed trait Identifier {
    val id: String
  }

  /**
   * Messages that include a payload implement `Payload`.
   */
  sealed trait Payload[P] {
    val payload: P
  }

  /**
   * Client-produced streaming messages.
   */
  sealed trait FromClient extends Product with Serializable

  object FromClient {

    /**
     * Starts communication with the server.  The client may expect a
     * `ConnectionAck` or `ConnectionError` in response.
     *
     * @param payload any connection parameters that the client wishes to send
     */
    final case class ConnectionInit(payload: Map[String, String] = Map.empty)
      extends FromClient
         with Payload[Map[String, String]]

    object ConnectionInit {
      implicit val EqConnectionInit: Eq[ConnectionInit] =
        Eq.by(_.payload)
    }

    /**
     * Starts a GraphQL operation.  The operation contains an id so that it can
     * be explicitly stopped by the client and so that data associated with the
     * operation coming from the server may identified.
     *
     * @param id identifier of the operation to start
     * @param payload the GraphQL request itself
     */
    final case class Start(id: String, payload: GraphQLRequest)
      extends FromClient
         with Identifier
         with Payload[GraphQLRequest]

    object Start {
      implicit val EqStart: Eq[Start] =
        Eq.by(a => (a.id, a.payload))
    }

    /**
     * Stops a running GraphQL operation (for example, a subscription).
     *
     * @param id identifier of the operation that was previously started
     */
    final case class Stop(id: String)
      extends FromClient
         with Identifier

    object Stop {
      implicit val EqStop: Eq[Stop] =
        Eq.by(_.id)
    }

    /**
     * Informs the server that the client wishes to terminate the connection.
     */
    final case object ConnectionTerminate
      extends FromClient

    implicit val EqFromClient: Eq[FromClient] =
      Eq.instance {
        case (a: ConnectionInit, b: ConnectionInit)     => a === b
        case (a: Start, b: Start)                       => a === b
        case (a: Stop, b: Stop)                         => a === b
        case (ConnectionTerminate, ConnectionTerminate) => true
        case _                                          => false
      }
  }

  /**
   * Server-produced streaming messages.
   */
  sealed trait FromServer extends Product with Serializable

  object FromServer {

    /**
     * A server acknowledgement and acceptance of a `ConnectionInit` request.
     */
    final case object ConnectionAck
      extends FromServer

    /**
     * A server rejection of a `ConnectionInit` request.
     *
     * @param payload error information
     */
    final case class ConnectionError(payload: Json)
      extends FromServer
         with Payload[Json]

    object ConnectionError {
      implicit val EqConnectionError: Eq[ConnectionError] =
        Eq.by(_.payload)
    }

    /**
     * Server initiated message that keeps the client connection alive.
     */
    final case object ConnectionKeepAlive
      extends FromServer

    final case class DataWrapper(data: Json)

    object DataWrapper {
      implicit val EqDataWrapper: Eq[DataWrapper] =
        Eq.by(_.data)
    }

    /**
     * GraphQL execution result from the server.  The result is associated with
     * an operation that was previously started by a `Start` message with the
     * associated `id`.
     *
     * @param id operation id
     * @param payload GraphQL result
     */
    final case class Data(id: String, payload: DataWrapper)
      extends FromServer
         with Identifier
         with Payload[DataWrapper]

    object Data {
      implicit val EqData: Eq[Data] =
        Eq.by(a => (a.id, a.payload))
    }

    final object DataJson {
      def unapply(data: Data): Option[(String, Json)] = Some((data.id, data.payload.data))
    }

    /**
     * Server-provided error information for a failed GraphQL operation,
     * previously started with a `Start` message with the associated `id`.
     *
     * @param id operation id
     * @param payload error information
     */
    final case class Error(id: String, payload: Json)
      extends FromServer
         with Identifier
         with Payload[Json]

    object Error {
      implicit val EqError: Eq[Error] =
        Eq.by(a => (a.id, a.payload))
    }

    /**
     * Message sent to the client indicating that no more data will be
     * forthcoming for the associated GraphQL operation.
     *
     * @param id operation id
     */
    final case class Complete(id: String)
      extends FromServer
         with Identifier

    object Complete {
      implicit val EqComplete: Eq[Complete] =
        Eq.by(_.id)
    }

    implicit val EqFromServer: Eq[FromServer] =
      Eq.instance {
        case (ConnectionAck, ConnectionAck)             => true
        case (a: ConnectionError, b: ConnectionError)   => a === b
        case (ConnectionKeepAlive, ConnectionKeepAlive) => true
        case (a: Data, b: Data)                         => a === b
        case (a: Error, b: Error)                       => a === b
        case (a: Complete, b: Complete)                 => a === b
        case _                                          => false
      }
  }
}
