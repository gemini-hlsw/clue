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

// An annotated `object` subquery: nothing is generated (it supplies its own `Data` via `.Typed`), but
// it still references `$ep`, so the generator infers and emits `type VariableDefs` for it, exactly as
// it does for the subqueries it generates.
@GraphQL
@GraphQLType("Query")
object StarWarsAnnotatedSubqueryVariable extends GraphQLSubquery.Typed[StarWars, Json] {
  override val subquery = gql"{ hero(episode: $$ep) { name } }"
}
// format: on
