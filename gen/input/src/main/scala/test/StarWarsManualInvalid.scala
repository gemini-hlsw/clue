// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  Clue.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
 */
package test

import clue.GraphQLOperation

// A hand-written operation: the code generator is NOT used here (no @GraphQL
// annotation), but the document must still be validated against the schema.
// The query references `invalidField`, which does not exist on the `Query`
// type, so the validation pass must report a diagnostic.
trait StarWarsManualInvalid extends GraphQLOperation[StarWars] {
  override val document: String = "query { invalidField }" // assert: GraphQLGen
}
// format: on
