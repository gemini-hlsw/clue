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
trait LucumaQuery3 extends GraphQLOperation[LucumaODB] {
  val document = """
      query {
        observations(programId: "p-2", first: 2147483647) {
          nodes {
            id
            observationTarget {
              ... on Target {
                target_id: id
                target_name: name
              }
              ... on Asterism {
                asterism_id: id
                asterism_name: name
              }
            }
          }
        }
      }"""
}
// format: on
