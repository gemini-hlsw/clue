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

// Hand-written subquery using `GraphQLSubquery`. The selection set references
// `invalidField`, which doesn't exist on `Character`, so validation must report it.
@GraphQLType("Character")
abstract class StarWarsManualSubqueryInvalid extends GraphQLSubquery[StarWars] {
  override val subquery = gql"{ id invalidField }" // assert: GraphQLGen
}
// format: on
