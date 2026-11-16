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
import clue.annotation.GraphQLStub
import clue.annotation.GraphQLType

// A subquery processed by the generator (`@GraphQL`) declaring multiple root types. `id` and `name`
// have the same types on `Human` and `Droid`, so a single `Data` is generated for both.
@GraphQL
@GraphQLType("Human", "Droid")
abstract class StarWarsSubqueryMultiTypeGenerated extends GraphQLSubquery[StarWars] {
  override val subquery = gql"{ id name }"
}

@GraphQLStub
object StarWarsSubqueryMultiTypeGenerated
// format: on
