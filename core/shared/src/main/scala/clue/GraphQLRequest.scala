package clue

import io.circe.Json
import io.circe.generic.JsonCodec

// Request format from Spec: https://github.com/APIs-guru/graphql-over-http
// {
//   "query": "...",
//   "operationName": "...",
//   "variables": { "myVariable": "someValue", ... }
// }

@JsonCodec
protected[clue] final case class GraphQLRequest(
  query:         String,
  operationName: Option[String] = None,
  variables:     Option[Json] = None
)
