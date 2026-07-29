// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  Clue.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
  // Opt-out fixture: the default is `true`
  Clue.descriptor = false
 */
package test

import clue.GraphQLOperation
import clue.annotation.GraphQL

@GraphQL
trait StarWarsDescriptorQuery extends GraphQLOperation[StarWars] {
  override val document: String = """
        query ($charId: ID!) {
          character(id: $charId) {
            id
            name
          }
        }
      """
}
// format: on
