// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  Clue.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
 */
package test

import clue.GraphQLOperation

// Hand-written operation using `GraphQLOperation.Typed` with a valid document.
// Validation must NOT report any diagnostic.
abstract class StarWarsManualTypedValid
    extends GraphQLOperation.Typed[StarWars, Map[String, Int], Int] {
  override val document: String = "query { hero(episode: NEWHOPE) { id name } }"
}
// format: on
