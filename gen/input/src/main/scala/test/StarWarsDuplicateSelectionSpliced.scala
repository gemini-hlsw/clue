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

// The same subquery is spliced on `friends` at the base level AND again inside the variant
// fragment. A splice contributes an ordinary param typed with the subquery's `Data` (and no nested
// class of its own), so the two must merge into a single `friends` param whose type the variant's
// `override val` shares with the trait's.
@GraphQL
trait StarWarsDuplicateSelectionSpliced extends GraphQLOperation[StarWars] {
  override val document = gql"""
        query ($$ep: Episode!) {
          hero(episode: $$ep) {
            __typename
            friends $StarWarsSubquery
            ... on Human {
              friends $StarWarsSubquery
              homePlanet
            }
          }
        }
      """
}
// format: on
