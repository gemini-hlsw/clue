// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  GraphQLGen.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
 */
package test

import clue.GraphQLSubquery
import clue.annotation.GraphQL
import test.StarWars

@GraphQL
trait StarWarsSubquery extends GraphQLSubquery[StarWars] {

  override val rootType: String = "Character"

  override val subquery: String = """
        {
          name
        }
      """
}

@clue.annotation.GraphQLStub
object StarWarsSubquery
// format: on
