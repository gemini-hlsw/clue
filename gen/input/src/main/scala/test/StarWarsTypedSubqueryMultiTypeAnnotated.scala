// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  Clue.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
 */
package test

import clue.GraphQLSubquery
import clue.annotation.GraphQL
import clue.annotation.GraphQLType
import io.circe.Json

// An annotated subquery that supplies its own `Data` and declares multiple root types: there is
// nothing to generate, so it is validated against each type and copied without `@GraphQL`.
@GraphQL
@GraphQLType("Human", "Droid")
object StarWarsTypedSubqueryMultiTypeAnnotated extends GraphQLSubquery.Typed[StarWars, Json] {
  override val subquery = gql"{ id name }"
}
// format: on
