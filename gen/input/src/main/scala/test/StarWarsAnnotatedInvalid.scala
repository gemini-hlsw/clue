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
import clue.annotation.GraphQL

// An annotated (generated) operation with an invalid field. The generator now reports the error as
// a diagnostic and skips code generation (no output file => linter mode), instead of aborting.
@GraphQL
trait StarWarsAnnotatedInvalid extends GraphQLOperation[StarWars] {
  override val document = gql"query { invalidField }" // assert: GraphQLGen
}
// format: on
