// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  GraphQLGen.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
  GraphQLGen.jsClasses = true
 */
package test

import clue.GraphQLOperation
import clue.annotation.GraphQL
import test.StarWars

@GraphQL
trait StarWarsQueryJS extends GraphQLOperation[StarWars] {
  override val document: String = """
        query ($charId: ID!) {
          character(id: $charId) {
            id
            name
            ... on Human {
              homePlanet
            }
            friends {
              name
            }
            ... on Droid {
              primaryFunction
            }
          }
        }
      """
}
// format: on
