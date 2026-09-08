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

// Checks that an ALIASED `__typename` (`kind: __typename`) is used as the decoder
// discriminator key, AND is also generated as a regular field (`kind: String`) on the trait
// and every instance.
@GraphQL // assert: GraphQLGen
trait StarWarsAliasedTypename extends GraphQLOperation[StarWars] {
  override val document = gql"""
        query ($$ep: Episode!) {
          hero(episode: $$ep) {
            kind: __typename
            name
            ... on Human {
              homePlanet
            }
            ... on Droid {
              primaryFunction
            }
          }
        }
      """
}
// format: on
