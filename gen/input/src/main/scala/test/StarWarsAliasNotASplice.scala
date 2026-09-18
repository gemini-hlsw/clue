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

// An alias that merely looks like the internal splice placeholder (`clue_splice_N`) is an
// ordinary aliased `__typename` field, not a splice; this used to be misread as a broken splice
// (matched by `alias.startsWith("subquery")`) and crash the rule with a NumberFormatException.
@GraphQL
trait StarWarsAliasNotASplice extends GraphQLOperation[StarWars] {
  override val document = gql"""
        query {
          hero(episode: NEWHOPE) {
            subqueryCount: __typename
          }
        }
      """
}
// format: on
