// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off

package test

import clue.GraphQLSubquery
import clue.annotation.GraphQLType

// A subquery processed by the generator (`@GraphQL`) declaring multiple root types. `id` and `name`
// have the same types on `Human` and `Droid`, so a single `Data` is generated for both.

@GraphQLType("Human", "Droid") object StarWarsSubqueryMultiTypeGenerated extends GraphQLSubquery[StarWars] {
  import StarWars.Scalars._
  ignoreUnusedImportScalars()
  import StarWars.Enums._
  ignoreUnusedImportEnums()
  import StarWars.Types._
  ignoreUnusedImportTypes()
  override val subquery = gql"{ id name }"
  case class Data(val id: String, val name: Option[String] = None)
  object Data {
    val id: monocle.Lens[Data, String] = monocle.macros.GenLens[Data](_.id)
    val name: monocle.Lens[Data, Option[String]] = monocle.macros.GenLens[Data](_.name)
    implicit val eqData: cats.Eq[Data] = cats.Eq.fromUniversalEquals
    implicit val showData: cats.Show[Data] = cats.Show.fromToString
    implicit val jsonDecoderData: io.circe.Decoder[Data] = io.circe.generic.semiauto.deriveDecoder[Data]
  }
  val dataDecoder: io.circe.Decoder[Data] = Data.jsonDecoderData
}


// format: on
