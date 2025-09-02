// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import clue.model.GraphQLErrors

class GraphQLException(msg: String) extends Exception(msg)

case class ConnectionException(msg: String) extends GraphQLException(msg)

case class RemoteInitializationException(exception: Throwable)
    extends GraphQLException(
      s"The server returned an error on initialization: [${exception.getMessage}]"
    )

case object ConnectionNotInitializedException extends GraphQLException("Connection not initialized")

case class UnexpectedInternalStateException[S](when: String, state: S)
    extends GraphQLException(
      s"Unexpected internal state when [$when]. Cannot recover. State is [$state]"
    )

case class DisconnectedException(reason: String)
    extends GraphQLException(s"Connection was closed. Reason: $reason.")

case class InvalidSubscriptionOperationException(operation: String, subscriptionId: String)
    extends GraphQLException(
      s"Attempted [$operation] on non-existent subscription with id: [$subscriptionId]"
    )

case class InvalidInvocationException(msg: String)
    extends GraphQLException(s"Invalid invocation on the current state: $msg")

case class ServerMessageDecodingException(cause: io.circe.Error)
    extends GraphQLException(
      s"Exception decoding message received from server: [${cause.getMessage}]"
    )
case class UnexpectedServerMessageException[M, S](msg: M, state: S)
    extends GraphQLException(
      s"Unexpected message received from server. Message received: [$msg], current state: [$state]"
    )

case class ResponseException[D](errors: GraphQLErrors, data: Option[D])
    extends GraphQLException(errors.map(_.message).toList.mkString(", "))
