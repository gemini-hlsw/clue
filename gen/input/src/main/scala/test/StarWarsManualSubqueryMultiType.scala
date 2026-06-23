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

// A hand-written subquery declaring multiple root types (allowed for hand-written subqueries). The
// selection `{ id name }` is valid on both `Human` and `Droid`, so this must NOT report a diagnostic.
@GraphQLType("Human", "Droid")
abstract class StarWarsManualSubqueryMultiType extends GraphQLSubquery.Typed[StarWars, Json] {
  override val subquery: String = "{ id name }"
}
// format: on
