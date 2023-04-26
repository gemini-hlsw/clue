// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off

package test

import clue.GraphQLSubquery
import io.circe.Json
import test.StarWars


object StarWarsSubquery2 extends GraphQLSubquery.Typed[StarWars, Json]("Character") {
  override val subquery: String = """
        {
          name
        }
      """
}
// format: on
