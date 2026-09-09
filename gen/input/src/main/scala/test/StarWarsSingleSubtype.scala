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

// A single fragment on a subtype (`Human`): its fields are flattened into the parent class (no
// `sealed trait`, no `Other`), and `__typename` is not required. A response of another subtype
// (`Droid`) then fails to decode with a missing-field error.
@GraphQL
trait StarWarsSingleSubtype extends GraphQLOperation[StarWars] {
  override val document = gql"""
        query ($$ep: Episode!) {
          hero(episode: $$ep) {
            name
            ... on Human {
              homePlanet
            }
          }
        }
      """
}
// format: on
