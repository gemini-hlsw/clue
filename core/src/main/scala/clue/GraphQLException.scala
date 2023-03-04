// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import clue.model.GraphQLErrors

class GraphQLException(msg: String) extends Exception(msg)

case object ConnectionException extends GraphQLException("Could not establish connection")

case object DisconnectedException extends GraphQLException("Connection was closed")

case class InvalidSubscriptionIdException(id: String)
    extends GraphQLException(s"Invalid subscription id: $id")

case class ResponseException(errors: GraphQLErrors) extends GraphQLException(errors.toString)
