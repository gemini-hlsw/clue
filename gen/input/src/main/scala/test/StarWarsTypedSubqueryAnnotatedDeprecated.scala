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
import io.circe.Json

// An annotated (generated) `object` subquery using `GraphQLSubquery.Typed`. It is copied as-is (it
// supplies its own Data), but the selection is still validated: `primaryFunction` is deprecated, so
// a warning is reported (proving validation now runs on annotated `.Typed` objects).
@GraphQL // assert: GraphQLGen
@GraphQLType("Droid")
object StarWarsTypedSubqueryAnnotatedDeprecated
    extends GraphQLSubquery.Typed[StarWars, Json] {
  override val subquery: String = """
        {
          primaryFunction
        }
      """
}
// format: on
