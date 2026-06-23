// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  Clue.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
 */
package test

import clue.GraphQLSubquery
import clue.annotation.GraphQLType
import io.circe.Json

// A hand-written `object` subquery using Scala 3 significant-indentation syntax (no braces), as in
// real usage. `invalidField` doesn't exist on `Character`, so validation must report it.
@GraphQLType("Character")
object StarWarsTypedSubqueryScala3 extends GraphQLSubquery.Typed[StarWars, Json]:
  override val subquery: String = "{ id invalidField }" // assert: GraphQLGen
// format: on
