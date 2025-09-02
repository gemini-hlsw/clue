// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model

import cats.Eq
import cats.derived.*
import cats.syntax.all.*
import io.circe.Json
import io.circe.JsonObject

/**
 * GraphQL web socket protocol streaming messages. Messages are cleanly divided in those coming
 * `FromClient` and those coming `FromServer`. See also
 * https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md.
 */
object StreamingMessage:

  /**
   * Messages implement `Identifier` to distinguish (potentially) long-running operations like
   * subscriptions where results come in over time.
   */
  sealed trait Identifier:
    val id: String

  /**
   * Messages that include a payload implement `Payload`.
   */
  sealed trait Payload[P]:
    val payload: P

  /**
   * Client-produced streaming messages.
   */
  sealed trait FromClient extends Product with Serializable derives Eq

  object FromClient:

    /**
     * Starts communication with the server. The client may expect a `ConnectionAck` in response.
     *
     * @param payload
     *   any connection parameters that the client wishes to send
     */
    final case class ConnectionInit(payload: Option[JsonObject] = none)
        extends FromClient
        with Payload[Option[JsonObject]] derives Eq

    object ConnectionInit:
      inline def apply(payload: JsonObject) = new ConnectionInit(payload.some)

    /**
     * Client initiated message that keeps the client connection alive.
     *
     * @param payload
     *   optional field can be used to transfer additional details about the pong
     */
    final case class Pong(payload: Option[JsonObject] = none)
        extends FromClient
        with Payload[Option[JsonObject]] derives Eq

    object Pong:
      inline def apply(payload: JsonObject) = new Pong(payload.some)

    /**
     * Starts a GraphQL operation. The operation contains an id so that it can be explicitly stopped
     * by the client and so that data associated with the operation coming from the server may
     * identified.
     *
     * @param id
     *   identifier of the operation to start
     * @param payload
     *   the GraphQL request itself
     */
    final case class Subscribe(id: String, payload: GraphQLRequest[JsonObject])
        extends FromClient
        with Identifier
        with Payload[GraphQLRequest[JsonObject]] derives Eq

    /**
     * Stops a running GraphQL operation (for example, a subscription).
     *
     * @param id
     *   identifier of the operation that was previously started
     */
    final case class Complete(id: String) extends FromClient with Identifier derives Eq
  end FromClient

  /**
   * Server-produced streaming messages.
   */
  sealed trait FromServer extends Product with Serializable derives Eq

  object FromServer:
    /**
     * A server acknowledgement and acceptance of a `ConnectionInit` request.
     */
    final case class ConnectionAck(payload: Option[JsonObject] = none)
        extends FromServer
        with Payload[Option[JsonObject]] derives Eq

    object ConnectionAck:
      inline def apply(payload: JsonObject) = new ConnectionAck(payload.some)

    /**
     * Server initiated message that keeps the client connection alive.
     *
     * @param payload
     *   optional field can be used to transfer additional details about the ping
     */
    final case class Ping(payload: Option[JsonObject] = none)
        extends FromServer
        with Payload[Option[JsonObject]] derives Eq

    object Ping:
      inline def apply(payload: JsonObject) = new Ping(payload.some)

    /**
     * GraphQL execution result from the server. The result is associated with an operation that was
     * previously started by a `Start` message with the associated `id`.
     *
     * @param id
     *   operation id
     * @param payload
     *   GraphQL result
     */
    final case class Next(id: String, payload: GraphQLResponse[Json])
        extends FromServer
        with Identifier
        with Payload[GraphQLResponse[Json]] derives Eq

    /**
     * Server-provided error information for a failed GraphQL operation, previously started with a
     * `Start` message with the associated `id`.
     *
     * @param id
     *   operation id
     * @param payload
     *   error information
     */
    final case class Error(id: String, payload: GraphQLErrors)
        extends FromServer
        with Identifier
        with Payload[GraphQLErrors] derives Eq

    /**
     * Message sent to the client indicating that no more data will be forthcoming for the
     * associated GraphQL operation.
     *
     * @param id
     *   operation id
     */
    final case class Complete(id: String) extends FromServer with Identifier derives Eq
  end FromServer
