// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  Clue.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
 */
package test

import clue.GraphQLOperation

// The operation declares `$unused` but never references it. Per the GraphQL spec (All Variables
// Used) this is a validation error, so the validation pass must report a diagnostic. Unused
// detection is only skipped for documents that splice subqueries (see `validateParsed`).
trait StarWarsUnusedVariable extends GraphQLOperation[StarWars] {
  override val document = gql"query ($$unused: ID!) { hero(episode: NEWHOPE) { id } }" // assert: GraphQLGen
}
// format: on
