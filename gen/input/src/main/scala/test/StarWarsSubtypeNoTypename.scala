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

// A fragment on a subtype (`Human`) without a base-level `__typename`: the generated decoder
// couldn't tell a `Human` response from any other `Character`, so this must be rejected.
@GraphQL
trait StarWarsSubtypeNoTypename extends GraphQLOperation[StarWars] {
  override val document =
    gql"query ($$ep: Episode!) { hero(episode: $$ep) { name ... on Human { homePlanet } } }" // assert: GraphQLGen
}
// format: on
