// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off

package test

import clue.GraphQLSubquery
import clue.annotation.GraphQLType


@GraphQLType("Character") object StarWarsSubquery extends GraphQLSubquery[StarWars] {
  import StarWars.Scalars._
  ignoreUnusedImportScalars()
  import StarWars.Enums._
  ignoreUnusedImportEnums()
  import StarWars.Types._
  ignoreUnusedImportTypes()
  override val subquery = gql"""
        {
          name
        }
      """
  case class Data(val name: Option[String] = None)
  object Data {
    val name: monocle.Iso[Data, Option[String]] = monocle.Focus[Data](_.name)
    implicit val eqData: cats.Eq[Data] = cats.Eq.fromUniversalEquals
    implicit val showData: cats.Show[Data] = cats.Show.fromToString
    implicit val jsonDecoderData: io.circe.Decoder[Data] = io.circe.generic.semiauto.deriveDecoder[Data]
  }
  val dataDecoder: io.circe.Decoder[Data] = Data.jsonDecoderData
}


// format: on
