// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  Clue.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
 */
package test

import clue.GraphQLOperation

// A hand-written operation composing a subquery via interpolation. This is valid:
// `friends` is selected with the spliced subquery. Validation must NOT report an
// error here (the spliced subquery is validated where it's defined).
trait StarWarsManualInterpolated extends GraphQLOperation[StarWars] {
  override val document =
    gql"query { hero(episode: NEWHOPE) { id friends $StarWarsSubquery } }"
}
// format: on
