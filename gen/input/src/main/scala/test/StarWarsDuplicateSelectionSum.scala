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

// `name` is selected at the base level AND again inside both variant fragments (same response
// name): GraphQL merges them, so each variant's generated case class must carry `name` once (as
// `override val`), not twice. `Human` and `Droid` are Character's only implementors, so no `Other`.
@GraphQL // assert: GraphQLGen
trait StarWarsDuplicateSelectionSum extends GraphQLOperation[StarWars] {
  override val document = gql"""
        query ($$ep: Episode!) {
          hero(episode: $$ep) {
            __typename
            name
            ... on Human {
              name
              homePlanet
            }
            ... on Droid {
              name
              primaryFunction
            }
          }
        }
      """
}
// format: on
