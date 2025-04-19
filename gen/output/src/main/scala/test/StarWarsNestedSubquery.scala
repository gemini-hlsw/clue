// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off

package test

import clue.GraphQLSubquery


object StarWarsNestedSubquery extends GraphQLSubquery[StarWars]("Character") {
  import StarWars.Scalars._
  ignoreUnusedImportScalars()
  import StarWars.Enums._
  ignoreUnusedImportEnums()
  import StarWars.Types._
  ignoreUnusedImportTypes()
  override val subquery: String = s"""
        {
          id
          name
          ... on Human {
            homePlanet
          }
          friends $StarWarsSubquery
          ... on Droid {
            primaryFunction
          }
        }
      """
  case class Data(val id: String, val name: Option[String] = None, val friends: Option[List[StarWarsSubquery.Data]] = None)
  object Data {
    val id: monocle.Lens[Data, String] = monocle.macros.GenLens[Data](_.id)
    val name: monocle.Lens[Data, Option[String]] = monocle.macros.GenLens[Data](_.name)
    val friends: monocle.Lens[Data, Option[List[StarWarsSubquery.Data]]] = monocle.macros.GenLens[Data](_.friends)
    implicit val eqData: cats.Eq[Data] = cats.Eq.fromUniversalEquals
    implicit val showData: cats.Show[Data] = cats.Show.fromToString
    implicit val jsonDecoderData: io.circe.Decoder[Data] = io.circe.generic.semiauto.deriveDecoder[Data]
  }
  val dataDecoder: io.circe.Decoder[Data] = Data.jsonDecoderData
}


// format: on
