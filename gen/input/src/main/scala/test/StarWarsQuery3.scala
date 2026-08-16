// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  Clue.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
 */
package test

import clue.GraphQLOperation
import clue.gql
import clue.annotation.GraphQL

@GraphQL // assert: GraphQLGen
trait StarWarsQuery3 extends GraphQLOperation[StarWars] {
  override val document = gql"""
        query ($$charId: ID!) {
          character(id: $$charId) {
            id
            name
            ... on Human {
              homePlanet
            }
            friends $StarWarsSubquery
            ... on Droid {
              primaryFunction
            }
          }
        }
      """
}
// format: on
