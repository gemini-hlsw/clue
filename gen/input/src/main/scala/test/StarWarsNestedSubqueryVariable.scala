// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  Clue.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
 */
package test

import clue.GraphQLOperation
import clue.GraphQLSubquery
import clue.gql
import clue.annotation.GraphQLType
import io.circe.Json

// A subquery referencing a variable (in a directive, the only argument a `Character` field takes in
// this schema), declared via `type VariableDefs`.
@GraphQLType("Character")
object StarWarsSubqueryDirectiveVariable extends GraphQLSubquery.Typed[StarWars, Json] {
  type VariableDefs = "($skipId: Boolean!)"
  override val subquery = gql"{ id @skip(if: $$skipId) name }"
}

// A subquery that splices the one above: `gql` requires it to declare the child's variable in its own
// `VariableDefs`, even though its own text doesn't reference it. The self-check must not report the
// declaration as unused.
@GraphQLType("Character")
object StarWarsNestedSubqueryVariable extends GraphQLSubquery.Typed[StarWars, Json] {
  type VariableDefs = "($skipId: Boolean!)"
  override val subquery = gql"{ name friends $StarWarsSubqueryDirectiveVariable }"
}

// And the operation must declare it too: the requirement propagates transitively, because the parent
// subquery had to declare it to splice its child.
trait StarWarsNestedVariableQuery extends GraphQLOperation[StarWars] {
  override val document =
    gql"query ($$skipId: Boolean!) { hero(episode: NEWHOPE) $StarWarsNestedSubqueryVariable }"
}
// format: on
