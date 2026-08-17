// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
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

// A subquery processed by the generator (`@GraphQL`) must declare exactly one `@GraphQLType`, since
// generation targets a single type. Multiple types here is an error (and nothing is generated).
@GraphQL // assert: GraphQLGen
@GraphQLType("Human", "Droid")
abstract class StarWarsSubqueryMultiTypeGenerated extends GraphQLSubquery[StarWars] {
  override val subquery = gql"{ id name }"
}
// format: on
