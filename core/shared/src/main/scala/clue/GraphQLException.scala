// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.syntax.traverse._
import clue.model.GraphQLError
import clue.model.json.DecoderGraphQLError
import io.circe.{ Decoder, Json }

class GraphQLException(msg: String) extends Exception(msg)

class ConnectionException() extends GraphQLException("Could not establish connection")

class DisconnectedException() extends GraphQLException("Connection was closed")

class InvalidSubscriptionIdException(id: String)
    extends GraphQLException(s"Invalid subscription id: $id")

class ResponseException(errors: List[Json])
    extends GraphQLException(errors.map(_.spaces2).mkString(",")) {

  /**
   * Decodes and returns the errors as `GraphQLError` if possible.
   */
  def asGraphQLErrors: List[GraphQLError] =
    errors.traverse(Decoder[GraphQLError].decodeJson).getOrElse(List.empty)

}
