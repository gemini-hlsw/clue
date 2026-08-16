// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  Clue.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
 */
package test

import clue.GraphQLOperation
import clue.gql
import clue.annotation.GraphQL

@GraphQL
trait StarWarsInclude extends GraphQLOperation[StarWars] {
  override val document = gql"""
        query ($$humanId: ID!, $$skipId: Boolean!, $$withName: Boolean!) {
          human(id: $$humanId) {
            id @skip(if: $$skipId)
            name @include(if: $$withName)
            homePlanet
          }
        }
      """
}
// format: on
