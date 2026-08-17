// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  Clue.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
 */
package test

import clue.GraphQLSubquery
import clue.annotation.GraphQL
import clue.annotation.GraphQLType

@GraphQL
@GraphQLType("Character")
abstract class StarWarsSubquery extends GraphQLSubquery[StarWars] {

  override val subquery = gql"""
        {
          name
        }
      """
}

@clue.annotation.GraphQLStub
object StarWarsSubquery
// format: on
