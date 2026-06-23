// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  Clue.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
 */
package test

import clue.GraphQLOperation
import clue.annotation.GraphQLType

// `@GraphQLType` declares a subquery's root type; it is meaningless on a `GraphQLOperation` and must
// be reported as an error.
@GraphQLType("Query") // assert: GraphQLGen
abstract class StarWarsTypeOnOperation extends GraphQLOperation[StarWars] {
  override val document: String = "query { hero(episode: NEWHOPE) { id } }"
}
// format: on
