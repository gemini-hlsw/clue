// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  GraphQLGen.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
  GraphQLGen.scalaJsReactReuse = true
 */
package test

import clue.GraphQLOperation
import clue.annotation.GraphQL
// gql: import japgolly.scalajs.react.Dummy._

object Wrapper /* gql: extends Something */ {
  @GraphQL
  trait StarWarsQuery2 extends GraphQLOperation[StarWars] {
  override val document: String = """
        fragment fields on Character {
          id
          name
          ... on Human {
            homePlanet
          }
          friends {
            name
          }
          ... on Droid {
            primaryFunction
          }
        }
        query ($charId: ID!) {
          character(id: $charId) {
            ...fields
          }
        }
      """
}
}
// format: on
