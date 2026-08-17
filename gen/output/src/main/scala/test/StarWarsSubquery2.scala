// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off

package test

import clue.GraphQLSubquery
import clue.gql
import clue.annotation.GraphQLType
import io.circe.Json


@GraphQLType("Character") object StarWarsSubquery2 extends GraphQLSubquery.Typed[StarWars, Json] {
  override val subquery = gql"""
        {
          name
        }
      """
}
// format: on
