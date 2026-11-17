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

// `homePlanet` exists on `Human` but not on `Droid`: a generated subquery is validated against
// every declared type too, so it must be reported for `Droid` (and nothing is generated).
@GraphQL
@GraphQLType("Human", "Droid")
abstract class StarWarsSubqueryMultiTypeInvalid extends GraphQLSubquery[StarWars] {
  override val subquery = gql"{ id homePlanet }" // assert: GraphQLGen
}
// format: on
