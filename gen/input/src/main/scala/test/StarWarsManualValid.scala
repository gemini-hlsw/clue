// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  Clue.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
 */
package test

import clue.GraphQLOperation

// A hand-written operation (no @GraphQL annotation) with a valid document.
// The validation pass must NOT report any diagnostic; an unexpected diagnostic
// would fail this testkit case.
trait StarWarsManualValid extends GraphQLOperation[StarWars] {
  override val document: String = "query { hero(episode: NEWHOPE) { id name } }"
}
// format: on
