// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.macros

object Schemas {
  @GraphQLSchema(debug = false)
  object Explore {
    object Scalars {
      type Cloudcover       = String
      type Imagequality     = String
      type Skybackground    = String
      type Watervapor       = String
      type Obsstatus        = String
      type Targetobjecttype = String
    }
  }

  @GraphQLSchema(debug = false)
  object LucumaODB {
    object Scalars {
      type AsterismId    = String
      type BigDecimal    = scala.BigDecimal
      type DmsString     = String
      type EpochString   = String
      type HmsString     = String
      type Long          = scala.Long
      type ObservationId = String
      type ProgramId     = String
      type TargetId      = String
    }
  }

  @GraphQLSchema(debug = false)
  object StarWars
}
