// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model

import cats.Eq

// Request format from Spec: https://github.com/APIs-guru/graphql-over-http
// {
//   "query": "...",
//   "operationName": "...",
//   "variables": { "myVariable": "someValue", ... }
// }

/**
 * A raw (pre-parsed) GraphQL request.
 *
 * @param query
 *   the query itself
 * @param operationName
 *   identifies a particular operation in the query to execute
 * @param variables
 *   values corresponding to variables in the query
 */
final case class GraphQLRequest[V](
  query:         String,
  operationName: Option[String] = None,
  variables:     Option[V] = None
)

object GraphQLRequest {
  implicit def eqGraphQLRequest[V: Eq]: Eq[GraphQLRequest[V]] =
    Eq.by(a => (a.query, a.operationName, a.variables))
}
