// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  Clue.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
 */
package test

import clue.GraphQLOperation

// A hand-written operation that splices a subquery AND has a genuine error: `invalidField`
// doesn't exist on `Character`. Validation must still report it (the spliced subquery must not
// mask the error), so a diagnostic is expected on the document.
trait StarWarsManualInterpolatedInvalid extends GraphQLOperation[StarWars] {
  override val document: String =
    s"query ($$charId: ID!) { character(id: $$charId) { invalidField friends $StarWarsSubquery } }" // assert: GraphQLGen
}
// format: on
