// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  GraphQLGen.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
 */
package test

import clue.GraphQLSubquery
import clue.annotation.GraphQL

@GraphQL
abstract class StarWarsNestedSubquery extends GraphQLSubquery[StarWars]("Character") {

  override val subquery: String = s"""
        {
          id
          name
          ... on Human {
            homePlanet
          }
          contacts:friends $StarWarsSubquery
          ... on Droid {
            primaryFunction
          }
        }
      """
}

@clue.annotation.GraphQLStub
object StarWarsNestedSubquery
// format: on
