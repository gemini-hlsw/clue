// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  Clue.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
 */
package test

import clue.GraphQLOperation
import clue.gql

// A hand-written operation with a query parameter AND a spliced subquery. The `$$`
// escapes the GraphQL variable (so it survives interpolation), while `$StarWarsSubquery`
// is a real splice. This must validate with NO diagnostic.
trait StarWarsManualParamInterpolated extends GraphQLOperation[StarWars] {
  override val document =
    gql"query ($$charId: ID!) { character(id: $$charId) { id friends $StarWarsSubquery } }"
}
// format: on
