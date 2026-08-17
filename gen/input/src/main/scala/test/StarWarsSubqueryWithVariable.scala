// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  Clue.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
 */
package test

import clue.GraphQLSubquery
import clue.gql
import clue.annotation.GraphQLType

// A subquery that references a variable (`$ep`), declared via `type VariableDefs`. The declaration is
// checked against usage (`$ep` feeds `hero(episode: Episode!)`) and the selection is valid, so this
// must NOT report any diagnostic.
@GraphQLType("Query")
abstract class StarWarsSubqueryWithVariable extends GraphQLSubquery[StarWars] {
  type VariableDefs = "($ep: Episode!)"
  override val subquery = gql"{ hero(episode: $$ep) { name } }"
}
// format: on
