// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  Clue.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
 */
package test

import clue.GraphQLSubquery
import clue.gql
import clue.annotation.GraphQLType

// A subquery that declares `$ep` via `type VariableDefs` AND has a genuine error (`invalidField`
// doesn't exist on `Character`). The declared variable is fine; field validation must still run and
// report the invalid field (the only diagnostic).
@GraphQLType("Query")
abstract class StarWarsSubqueryVariableInvalidField extends GraphQLSubquery[StarWars] {
  type VariableDefs = "($ep: Episode!)"
  override val subquery = gql"{ hero(episode: $$ep) { invalidField } }" // assert: GraphQLGen
}
// format: on
