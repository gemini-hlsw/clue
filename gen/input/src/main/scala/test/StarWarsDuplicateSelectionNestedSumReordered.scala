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

// The nested polymorphic `friends` is duplicated with its fragments in the opposite order; the
// response shape is identical, so it must merge into one `friends` param typed with the single
// base-level sealed trait `Data.Hero.Friends` (cases `Human`, `Droid`, no `Other` since Character
// has only those two implementors).
@GraphQL // assert: GraphQLGen
trait StarWarsDuplicateSelectionNestedSumReordered extends GraphQLOperation[StarWars] {
  override val document = gql"""
        query ($$ep: Episode!) {
          hero(episode: $$ep) {
            __typename
            friends {
              __typename
              name
              ... on Human { homePlanet }
              ... on Droid { primaryFunction }
            }
            ... on Human {
              friends {
                __typename
                name
                ... on Droid { primaryFunction }
                ... on Human { homePlanet }
              }
              homePlanet
            }
          }
        }
      """
}
// format: on
