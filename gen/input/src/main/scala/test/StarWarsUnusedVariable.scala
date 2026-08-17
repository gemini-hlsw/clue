// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  Clue.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
 */
package test

import clue.GraphQLOperation

// Unused-variable detection is disabled because grackle's `collectValueRefs` overwrites instead of
// accumulating variable references, falsely flagging variables that appear alongside others (e.g.
// several `$var` fields in one input object). So even this genuinely-unused `$unused` must NOT
// produce a diagnostic.
trait StarWarsUnusedVariable extends GraphQLOperation[StarWars] {
  override val document = gql"query ($$unused: ID!) { hero(episode: NEWHOPE) { id } }"
}
// format: on
