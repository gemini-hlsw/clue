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
import clue.gql

@GraphQL
trait LucumaSubscription extends GraphQLOperation[LucumaODB] {
  val document = gql"""
      subscription AsterismEdit($$programId: ProgramId) {
        asterismEdit(programId: $$programId) {
          editType
          value {
            id
            name
          }
        }
      }"""
}
// format: on
