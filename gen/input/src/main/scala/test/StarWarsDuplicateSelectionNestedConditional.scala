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

// The nested `id` is selected unconditionally at the base level and again with `@include` inside
// the variant fragment. GraphQL merges them, so `friends` must merge into one param typed with the
// single base-level `Data.Hero.Friends`, non-optional (the base occurrence).
@GraphQL
trait StarWarsDuplicateSelectionNestedConditional extends GraphQLOperation[StarWars] {
  override val document = gql"""
        query ($$ep: Episode!, $$withId: Boolean!) {
          hero(episode: $$ep) {
            __typename
            friends {
              id
            }
            ... on Human {
              friends {
                id @include(if: $$withId)
              }
              homePlanet
            }
          }
        }
      """
}
// format: on
