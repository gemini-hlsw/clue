// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off
/*
  rules = [GraphQLGen]
  GraphQLGen.schemaDirs = ["gen/input/src/main/resources/graphql/schemas"]
  GraphQLGen.jitDecoder = true
 */
package test

import clue.annotation.GraphQLSchema

@GraphQLSchema
trait LucumaODB {
  object Scalars {
    type AsterismId      = String
    type BigDecimal      = scala.BigDecimal
    type DmsString       = String
    type EpochString     = String
    type HmsString       = String
    type Long            = scala.Long
    type ObservationId   = String
    type ProgramId       = String
    type TargetId        = String
    type NonEmptyString  = String
    type ConstraintSetId = String
  }
}
// format: on
