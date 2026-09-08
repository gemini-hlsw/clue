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

// A single fragment on a subtype (`Human`), leaving `Droid` uncovered: `Character` has exactly
// two implementors, so this must still generate a fallback `Other` instance (no special-casing
// for "just one variant").
@GraphQL
trait StarWarsSingleSubtype extends GraphQLOperation[StarWars] {
  override val document = gql"""
        query ($$ep: Episode!) {
          hero(episode: $$ep) {
            __typename
            name
            ... on Human {
              homePlanet
            }
          }
        }
      """
}
// format: on
