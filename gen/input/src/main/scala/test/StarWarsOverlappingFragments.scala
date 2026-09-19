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

// Human and Pilot both cover the concrete type Human, so one case would be unreachable and its
// fields lost.
@GraphQL
trait StarWarsOverlappingFragments extends GraphQLOperation[StarWars] {
  override val document =
    gql"query ($$ep: Episode!) { hero(episode: $$ep) { __typename name ... on Human { homePlanet } ... on Pilot { vehicle } } }" // assert: GraphQLGen
}
// format: on
