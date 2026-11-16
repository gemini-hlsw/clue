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

// `name` is nullable on `Human` but not on `Starship`, so the two root types would generate a
// different `Data`. Since a single one is emitted, this is an error (and nothing is generated).
@GraphQL // assert: GraphQLGen
@GraphQLType("Human", "Starship")
abstract class StarWarsSubqueryMultiTypeMismatch extends GraphQLSubquery[StarWars] {
  override val subquery = gql"{ id name }"
}
// format: on
