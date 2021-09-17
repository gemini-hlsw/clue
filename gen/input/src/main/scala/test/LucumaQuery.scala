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
trait LucumaQuery extends GraphQLOperation[LucumaODB] {
  val document = """
      query Program {
        program(programId: "p-2") {
          id
          name
          targets(first: 10, includeDeleted: true) {
            nodes {
              id
              name
              tracking {
                tracktype: __typename
                ... on Sidereal {
                  epoch
                }
                ... on Nonsidereal {
                  keyType
                }
              }
            }
          }
        }
      }"""
}
// format: on
