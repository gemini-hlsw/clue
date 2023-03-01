// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

// import cats.syntax.traverse._
import clue.model.GraphQLError
// import clue.model.json.DecoderGraphQLError
// import io.circe.Decoder
// import io.circe.Json
import cats.data.NonEmptyList

class GraphQLException(msg: String) extends Exception(msg)

case object ConnectionException extends GraphQLException("Could not establish connection")

case object DisconnectedException extends GraphQLException("Connection was closed")

case class InvalidSubscriptionIdException(id: String)
    extends GraphQLException(s"Invalid subscription id: $id")

// class ResponseException(errors: List[Json])
// extends GraphQLException(errors.map(_.spaces2).mkString(",")) {
case class ResponseException(errors: NonEmptyList[GraphQLError])
    extends GraphQLException(errors.toString) {

  // /**
  //  * Decodes and returns the errors as a list of `GraphQLError` if possible.
  //  *
  //  * @return
  //  *   None if there is a problem parsing the JSON as an array of GraphQLError,
  //  *   Some(List[GraphQLError]) if successful
  //  */
  // def asGraphQLErrors: Option[List[GraphQLError]] =
  //   errors.traverse(Decoder[GraphQLError].decodeJson).toOption

}
