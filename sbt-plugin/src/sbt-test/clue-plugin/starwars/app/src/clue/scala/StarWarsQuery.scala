// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off

package test

import clue.GraphQLOperation
import clue.gql
import clue.annotation.GraphQL
import test.StarWars

@GraphQL
trait StarWarsQuery extends GraphQLOperation[StarWars] {
  override val document = gql"""
        query ($$charId: ID!) {
          character(id: $$charId) {
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
