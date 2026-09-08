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

// `__typename` inside an unconditional same-type fragment is pure grouping and lands flat in the
// response, so it satisfies the discriminator requirement.
@GraphQL
trait StarWarsTypenameInFragment extends GraphQLOperation[StarWars] {
  override val document = gql"""
        query ($$ep: Episode!) {
          hero(episode: $$ep) {
            ... on Character { __typename }
            name
            ... on Human {
              homePlanet
            }
          }
        }
      """
}
// format: on
