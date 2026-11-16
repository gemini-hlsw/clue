// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  Clue.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
 */
package test

import clue.GraphQLSubquery
import clue.annotation.GraphQL
import clue.annotation.GraphQLType

// `$ep` is inferred as `Episode!` from `Query.hero` but as `Episode` from `Starship.hero`. A single
// `type VariableDefs` is emitted, so the variables must be declared explicitly instead.
@GraphQL
@GraphQLType("Query", "Starship")
abstract class StarWarsSubqueryMultiTypeVariableMismatch extends GraphQLSubquery[StarWars] {
  override val subquery = gql"{ hero(episode: $$ep) { name } }" // assert: GraphQLGen
}
// format: on
