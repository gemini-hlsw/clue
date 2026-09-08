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

// Combines an ALIASED `__typename` discriminator with a fallback `Other` instance (`Droid` is
// left uncovered): `kind` must still be generated as a regular field on `Other` too.
@GraphQL
trait StarWarsAliasedTypenameOther extends GraphQLOperation[StarWars] {
  override val document = gql"""
        query ($$ep: Episode!) {
          hero(episode: $$ep) {
            kind: __typename
            name
            ... on Human {
              homePlanet
            }
          }
        }
      """
}
// format: on
