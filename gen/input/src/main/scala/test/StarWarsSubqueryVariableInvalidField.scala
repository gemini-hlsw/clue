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

// A subquery that references a variable (`$ep`) AND has a genuine error (`invalidField` doesn't
// exist on `Character`). The variable must not be reported as undefined, but field validation must
// still run and report the invalid field.
@GraphQLType("Query")
abstract class StarWarsSubqueryVariableInvalidField extends GraphQLSubquery[StarWars] {
  override val subquery: String = "{ hero(episode: $ep) { invalidField } }" // assert: GraphQLGen
}
// format: on
