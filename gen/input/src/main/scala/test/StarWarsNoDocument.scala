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

// An annotated operation that is missing its `document`. This is a structural error: it is now
// reported as a diagnostic (rather than aborting the run), and code generation is skipped.
@GraphQL // assert: GraphQLGen
trait StarWarsNoDocument extends GraphQLOperation[StarWars]
// format: on
