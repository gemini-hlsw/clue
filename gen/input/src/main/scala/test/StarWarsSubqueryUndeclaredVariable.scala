// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  Clue.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
 */
package test

import clue.GraphQLSubquery
import clue.annotation.GraphQLType

// A subquery that uses `$ep` but does NOT declare it via `type Variables`. Under the stricter check
// (variables must be declared) this is an undefined-variable error.
@GraphQLType("Query")
abstract class StarWarsSubqueryUndeclaredVariable extends GraphQLSubquery[StarWars] {
  override val subquery: String = "{ hero(episode: $ep) { name } }" // assert: GraphQLGen
}
// format: on
