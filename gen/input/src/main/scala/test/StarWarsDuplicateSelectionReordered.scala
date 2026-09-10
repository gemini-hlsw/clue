// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  Clue.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
 */
package test

import clue.GraphQLOperation
import clue.annotation.GraphQL

// The same nested selection with its fields in a different order is still one selection (GraphQL
// field order is insignificant), so it must merge exactly like `StarWarsDuplicateSelectionNested`,
// keeping the base level's field order.
@GraphQL
trait StarWarsDuplicateSelectionReordered extends GraphQLOperation[StarWars] {
  override val document = gql"""
        query ($$ep: Episode!) {
          hero(episode: $$ep) {
            __typename
            friends {
              id
              name
            }
            ... on Human {
              friends {
                name
                id
              }
              homePlanet
            }
          }
        }
      """
}
// format: on
