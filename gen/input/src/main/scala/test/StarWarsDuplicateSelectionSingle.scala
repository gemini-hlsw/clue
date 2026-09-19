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

// `name` is selected at the base level AND again inside the single variant fragment (same
// response name): GraphQL merges them, so the generator must too, or the generated case class
// would get a duplicate `name` param.
@GraphQL
trait StarWarsDuplicateSelectionSingle extends GraphQLOperation[StarWars] {
  override val document = gql"""
        query ($$ep: Episode!) {
          hero(episode: $$ep) {
            __typename
            name
            ... on Human {
              __typename
              name
              homePlanet
            }
          }
        }
      """
}
// format: on
