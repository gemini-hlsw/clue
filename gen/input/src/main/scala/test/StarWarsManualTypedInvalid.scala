// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  Clue.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
 */
package test

import clue.GraphQLOperation
import clue.gql

// Hand-written operation using `GraphQLOperation.Typed`. The document references
// `invalidField`, which doesn't exist on `Query`, so validation must report it.
abstract class StarWarsManualTypedInvalid
    extends GraphQLOperation.Typed[StarWars, Map[String, Int], Int] {
  override val document = gql"query { invalidField }" // assert: GraphQLGen
}
// format: on
