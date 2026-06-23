// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLValidate]
  Clue.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
 */
package test

import clue.GraphQLOperation
import clue.annotation.GraphQL

// Under `GraphQLValidate` (validation only, as used by `clueCheck`), an `@GraphQL` annotation is
// misplaced — it's only processed by the generator in the clue source directory. The rule reports
// an error and does not generate.
@GraphQL // assert: GraphQLValidate
trait StarWarsAnnotatedUnderValidate extends GraphQLOperation[StarWars] {
  override val document: String = "query { hero(episode: NEWHOPE) { id } }"
}
// format: on
