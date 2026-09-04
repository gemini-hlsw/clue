// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  Clue.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
 */
package test

import clue.GraphQLSubquery
import clue.annotation.GraphQLType

// A subquery declaring `$ep: String!` but using it where `Episode!` is required (`hero(episode:)`).
// The declared variable type is incompatible with its usage — an error.
@GraphQLType("Query")
abstract class StarWarsSubqueryWrongVariableType extends GraphQLSubquery[StarWars] {
  type VariableDefs = "($ep: String!)"
  override val subquery = gql"{ hero(episode: $$ep) { name } }" // assert: GraphQLGen
}
// format: on
