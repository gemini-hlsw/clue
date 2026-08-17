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

// Hand-written subquery using `GraphQLSubquery.Typed` with a valid selection set on
// `Character`. Validation must NOT report any diagnostic.
@GraphQLType("Character")
abstract class StarWarsManualSubqueryTypedValid
    extends GraphQLSubquery.Typed[StarWars, Int] {
  override val subquery = gql"{ id name }"
}
// format: on
