// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off

package test

import clue.GraphQLSubquery
import clue.annotation.GraphQLType

// A generated subquery that references `$ep` without declaring it: the generator infers the variable
// from usage (`hero(episode: Episode!)`) and emits `type VariableDefs = "($ep: Episode!)"`.

@GraphQLType("Query") object StarWarsSubqueryInferVariable extends GraphQLSubquery[StarWars] {
  import StarWars.Scalars._
  ignoreUnusedImportScalars()
  import StarWars.Enums._
  ignoreUnusedImportEnums()
  import StarWars.Types._
  ignoreUnusedImportTypes()
  type VariableDefs = "($ep: Episode!)"
  override val subquery = gql"{ hero(episode: $$ep) { name } }"
  case class Data(val hero: Data.Hero)
  object Data {
    case class Hero(val name: Option[String] = None)
    object Hero {
      val name: monocle.Iso[Data.Hero, Option[String]] = monocle.Focus[Data.Hero](_.name)
      implicit val eqHero: cats.Eq[Data.Hero] = cats.Eq.fromUniversalEquals
      implicit val showHero: cats.Show[Data.Hero] = cats.Show.fromToString
      implicit val jsonDecoderHero: io.circe.Decoder[Data.Hero] = io.circe.generic.semiauto.deriveDecoder[Data.Hero]
    }
    val hero: monocle.Iso[Data, Data.Hero] = monocle.Focus[Data](_.hero)
    implicit val eqData: cats.Eq[Data] = cats.Eq.fromUniversalEquals
    implicit val showData: cats.Show[Data] = cats.Show.fromToString
    implicit val jsonDecoderData: io.circe.Decoder[Data] = io.circe.generic.semiauto.deriveDecoder[Data]
  }
  val dataDecoder: io.circe.Decoder[Data] = Data.jsonDecoderData
}


// format: on
