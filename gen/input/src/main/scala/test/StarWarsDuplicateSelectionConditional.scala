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

// Non-null `id` is selected unconditionally at the base level and again with `@include` inside
// the variant fragment. GraphQL merges them; since the base occurrence is unconditional, the field
// is always present, so the merged param keeps the base level's non-optional `String` (which is
// also what the trait requires of the `override val`).
@GraphQL
trait StarWarsDuplicateSelectionConditional extends GraphQLOperation[StarWars] {
  override val document = gql"""
        query ($$ep: Episode!, $$withId: Boolean!) {
          hero(episode: $$ep) {
            __typename
            id
            ... on Human {
              id @include(if: $$withId)
              homePlanet
            }
          }
        }
      """
}
// format: on
