// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  Clue.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
 */
package test

import clue.GraphQLSubquery
import io.circe.Json

// A hand-written subquery with no `@GraphQLType` annotation: the root type can't be determined, so
// the selection can't be validated — a warning is reported (anchored at the definition) instead of
// silently skipping.
abstract class StarWarsSubqueryNoType extends GraphQLSubquery.Typed[StarWars, Json] { // assert: GraphQLGen
  override val subquery = gql"{ id name }"
}
// format: on
