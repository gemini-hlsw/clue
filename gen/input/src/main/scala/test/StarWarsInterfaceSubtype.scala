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

// A fragment on an interface (`Pilot`, implemented only by `Human`): `__typename` in a response is
// always the concrete object type name (`Human`), never the interface name, so the decoder must
// map every concrete implementor to the `Pilot` instance, not match on `"Pilot"` itself.
@GraphQL
trait StarWarsInterfaceSubtype extends GraphQLOperation[StarWars] {
  override val document = gql"""
        query ($$ep: Episode!) {
          hero(episode: $$ep) {
            __typename
            name
            ... on Pilot {
              vehicle
            }
          }
        }
      """
}
// format: on
