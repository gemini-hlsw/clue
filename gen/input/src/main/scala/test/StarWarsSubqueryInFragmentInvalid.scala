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

// A subquery is only legal as the *whole* selection set of a field (`field $Subquery`, see
// StarWarsQuery3). Here it's spliced as the body of an inline fragment instead, which `resolveData`
// doesn't recognize, so this must be rejected.
@GraphQL
trait StarWarsSubqueryInFragmentInvalid extends GraphQLOperation[StarWars] {
  override val document =
    gql"query ($$ep: Episode!) { hero(episode: $$ep) { __typename name ... on Human $StarWarsHumanSubquery } }" // assert: GraphQLGen
}
// format: on
