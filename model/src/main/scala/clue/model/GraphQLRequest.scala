// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model

import cats.Eq
import cats.syntax.option.*

// Request format from Spec: https://github.com/APIs-guru/graphql-over-http
// {
//   "query": "...",
//   "operationName": "...",
//   "variables": { "myVariable": "someValue", ... }
// }

/**
 * A GraphQL request.
 *
 * See https://spec.graphql.org/October2021/#sec-Execution
 *
 * @param query
 *   the query itself
 * @param operationName
 *   identifies a particular operation in the query to execute
 * @param variables
 *   values corresponding to variables in the query
 * @param extensions
 *   values for protocol extension
 */
final case class GraphQLRequest[V](
  query:         String,
  operationName: Option[String] = none,
  variables:     Option[V] = none,
  extensions:    Option[GraphQLExtensions] = none
)

object GraphQLRequest {
  implicit def eqGraphQLRequest[V: Eq]: Eq[GraphQLRequest[V]] =
    Eq.by(a => (a.query, a.operationName, a.variables, a.extensions))
}
