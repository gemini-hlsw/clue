// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  GraphQLGen.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
 */
package test

import clue.GraphQLOperation
import clue.annotation.GraphQL

@GraphQL
trait LucumaQuery2 extends GraphQLOperation[LucumaODB] {
  val document = """
      query Program {
        program(programId: "p-2") {
          id
          name
          targets(includeDeleted: true, first: 100) {
            nodes {
              id
              name
              tracking {
                ... on Sidereal {
                  epoch
                }
              }
            }
          }
        }
      }"""
}
// format: on
