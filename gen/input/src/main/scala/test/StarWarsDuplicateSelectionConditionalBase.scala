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

// The base-level field is duplicated with different optionality across a same-type fragment: `id`
// is selected with `@include` and again unconditionally via `... on Character` (a fragment on the
// field's own type, so plain grouping, not a variant). The base merge sees no `override` params and
// keeps the non-optional occurrence for the trait; the variant merge must pick the same occurrence
// among the base-level ones, so trait, variant and `Other` all agree on the non-optional type.
@GraphQL
trait StarWarsDuplicateSelectionConditionalBase extends GraphQLOperation[StarWars] {
  override val document = gql"""
        query ($$ep: Episode!, $$withId: Boolean!) {
          hero(episode: $$ep) {
            __typename
            id @include(if: $$withId)
            ... on Character { id }
            ... on Human { homePlanet }
          }
        }
      """
}
// format: on
