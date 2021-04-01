// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  GraphQLGen.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
 */
// format: on
package test

import clue.annotation.GraphQL
import clue.GraphQLOperation
import test2.StarWars

@GraphQL // We have to put the schema here as a parameter. See how simulacrum extracts annotation parameters.
trait StarWarsQueryGQL extends GraphQLOperation[StarWars] { // And do not extend. Will check object has document anyway. Generated code will extend.
  // And turn into object so that we can reference its document.

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
            __typename
          }
        }
      """
}
