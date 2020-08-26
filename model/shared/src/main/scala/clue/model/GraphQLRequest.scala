package clue.model

import cats.Eq
import cats.implicits._
import io.circe.Json

// Request format from Spec: https://github.com/APIs-guru/graphql-over-http
// {
//   "query": "...",
//   "operationName": "...",
//   "variables": { "myVariable": "someValue", ... }
// }

/**
 * A raw (pre-parsed) GraphQL request.
 *
 * @param query         the query itself
 * @param operationName identifies a particular operation in the query to
 *                      execute
 * @param variables     values corresponding to variables in the query
 */
final case class GraphQLRequest(
  query:         String,
  operationName: Option[String] = None,
  variables:     Option[Json]   = None
)

object GraphQLRequest {
  implicit val EqGraphQLRequest: Eq[GraphQLRequest] =
    Eq.by(a => (a.query, a.operationName, a.variables))
}