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

// Two subtypes (`Human`, `Droid`); `__typename` sits inside a same-type fragment
// (`... on Character`) that itself carries `@include`: the fragment flattens to a plain
// `__typename` at the base level, but at runtime with the flag off the whole fragment (and
// therefore `__typename`) is absent from the response, so this must be rejected just like a
// directly-conditional `__typename`.
@GraphQL
trait StarWarsSubtypeConditionalTypenameFragment extends GraphQLOperation[StarWars] {
  override val document =
    gql"query ($$ep: Episode!, $$withType: Boolean!) { hero(episode: $$ep) { ... on Character @include(if: $$withType) { __typename } name ... on Human { homePlanet } ... on Droid { primaryFunction } } }" // assert: GraphQLGen
}
// format: on
