// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  Clue.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
 */
package test

import clue.GraphQLSubquery
import clue.annotation.GraphQLType
import io.circe.Json

// A hand-written subquery declaring multiple root types. `homePlanet` exists on `Human` but not on
// `Droid`, so validating the selection against every declared type must report it for `Droid`.
@GraphQLType("Human", "Droid")
abstract class StarWarsManualSubqueryMultiTypeInvalid extends GraphQLSubquery.Typed[StarWars, Json] {
  override val subquery: String = "{ id homePlanet }" // assert: GraphQLGen
}
// format: on
