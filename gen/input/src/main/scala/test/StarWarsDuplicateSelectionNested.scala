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

// `friends { name }` is selected at the base level AND again inside the variant fragment. Both
// must merge into one `friends` param on `Human`, typed with the base-level `Data.Hero.Friends`
// (emitted once, in `Hero`'s companion): a second `Friends` inside `Human` would shadow it and give
// the `override val` a type incompatible with the trait's.
@GraphQL
trait StarWarsDuplicateSelectionNested extends GraphQLOperation[StarWars] {
  override val document = gql"""
        query ($$ep: Episode!) {
          hero(episode: $$ep) {
            __typename
            friends {
              name
            }
            ... on Human {
              friends {
                name
              }
              homePlanet
            }
          }
        }
      """
}
// format: on
