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

// Two subtypes (`Human`, `Droid`) with a base-level `__typename` guarded by `@include`: at runtime
// with the flag off, the field is absent from the response and the generated decoder can't tell
// which subtype it got, so this must be rejected.
@GraphQL
trait StarWarsSubtypeConditionalTypename extends GraphQLOperation[StarWars] {
  override val document =
    gql"query ($$ep: Episode!, $$withType: Boolean!) { hero(episode: $$ep) { __typename @include(if: $$withType) name ... on Human { homePlanet } ... on Droid { primaryFunction } } }" // assert: GraphQLGen
}
// format: on
