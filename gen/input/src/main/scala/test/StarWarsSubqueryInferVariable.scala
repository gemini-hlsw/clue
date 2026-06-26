// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
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

// A generated subquery that references `$ep` without declaring it: the generator infers the variable
// from usage (`hero(episode: Episode!)`) and emits `type Variables = "($ep: Episode!)"`.
@GraphQL
@GraphQLType("Query")
abstract class StarWarsSubqueryInferVariable extends GraphQLSubquery[StarWars] {
  override val subquery: String = "{ hero(episode: $ep) { name } }"
}

@clue.annotation.GraphQLStub
object StarWarsSubqueryInferVariable
// format: on
