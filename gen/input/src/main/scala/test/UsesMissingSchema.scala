// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  Clue.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
 */
package test

import clue.GraphQLOperation
import clue.annotation.GraphQL

// A schema type with no corresponding `<name>.graphql` in `schemaDirs`. Loading it fails; that is
// now reported as a diagnostic (anchored at the operation) instead of aborting the whole run, and
// code generation is skipped.
trait NoSuchSchema

@GraphQL // assert: GraphQLGen
trait UsesMissingSchema extends GraphQLOperation[NoSuchSchema] {
  override val document = gql"query { whatever }"
}
// format: on
