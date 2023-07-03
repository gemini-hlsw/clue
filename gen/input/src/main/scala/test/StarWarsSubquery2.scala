// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  GraphQLGen.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
 */
package test

import clue.GraphQLSubquery
import clue.annotation.GraphQL
import io.circe.Json

@GraphQL
object StarWarsSubquery2 extends GraphQLSubquery.Typed[StarWars, Json]("Character") {

  override val subquery: String = """
        {
          name
        }
      """
}
// format: on
