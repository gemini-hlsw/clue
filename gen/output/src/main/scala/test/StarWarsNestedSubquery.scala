// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off

package test

import clue.GraphQLSubquery
import clue.annotation.GraphQLType


@GraphQLType("Character") object StarWarsNestedSubquery extends GraphQLSubquery[StarWars] {
  import StarWars.Scalars._
  ignoreUnusedImportScalars()
  import StarWars.Enums._
  ignoreUnusedImportEnums()
  import StarWars.Types._
  ignoreUnusedImportTypes()
  override val subquery = gql"""
        {
          __typename
          id
          name
          ... on Human {
            homePlanet
          }
          contacts:friends $StarWarsSubquery
          ... on Droid {
            primaryFunction
          }
        }
      """
  sealed trait Data {
    val id: String
    val name: Option[String]
    val contacts: Option[List[StarWarsSubquery.Data]]
  }
  object Data {
    case class Human(override val id: String, override val name: Option[String] = None, val homePlanet: Option[String] = None, override val contacts: Option[List[StarWarsSubquery.Data]] = None) extends Data()
    object Human {
      val id: monocle.Lens[Data.Human, String] = monocle.macros.GenLens[Data.Human](_.id)
      val name: monocle.Lens[Data.Human, Option[String]] = monocle.macros.GenLens[Data.Human](_.name)
      val homePlanet: monocle.Lens[Data.Human, Option[String]] = monocle.macros.GenLens[Data.Human](_.homePlanet)
      val contacts: monocle.Lens[Data.Human, Option[List[StarWarsSubquery.Data]]] = monocle.macros.GenLens[Data.Human](_.contacts)
      implicit val eqHuman: cats.Eq[Data.Human] = cats.Eq.fromUniversalEquals
      implicit val showHuman: cats.Show[Data.Human] = cats.Show.fromToString
      implicit val jsonDecoderHuman: io.circe.Decoder[Data.Human] = io.circe.generic.semiauto.deriveDecoder[Data.Human]
    }
    case class Droid(override val id: String, override val name: Option[String] = None, override val contacts: Option[List[StarWarsSubquery.Data]] = None, @deprecated("Use 'functions' instead") val primaryFunction: Option[String] = None) extends Data()
    object Droid {
      val id: monocle.Lens[Data.Droid, String] = monocle.macros.GenLens[Data.Droid](_.id)
      val name: monocle.Lens[Data.Droid, Option[String]] = monocle.macros.GenLens[Data.Droid](_.name)
      val contacts: monocle.Lens[Data.Droid, Option[List[StarWarsSubquery.Data]]] = monocle.macros.GenLens[Data.Droid](_.contacts)
      @deprecated("Use 'functions' instead") val primaryFunction: monocle.Lens[Data.Droid, Option[String]] = monocle.macros.GenLens[Data.Droid](_.primaryFunction)
      implicit val eqDroid: cats.Eq[Data.Droid] = cats.Eq.fromUniversalEquals
      implicit val showDroid: cats.Show[Data.Droid] = cats.Show.fromToString
      implicit val jsonDecoderDroid: io.circe.Decoder[Data.Droid] = io.circe.generic.semiauto.deriveDecoder[Data.Droid]
    }
    val id: monocle.Lens[Data, String] = monocle.Lens[Data, String](_.id) {
      v => _ match {
        case s: Data.Human =>
          s.copy(id = v)
        case s: Data.Droid =>
          s.copy(id = v)
      }
    }
    val name: monocle.Lens[Data, Option[String]] = monocle.Lens[Data, Option[String]](_.name) {
      v => _ match {
        case s: Data.Human =>
          s.copy(name = v)
        case s: Data.Droid =>
          s.copy(name = v)
      }
    }
    val contacts: monocle.Lens[Data, Option[List[StarWarsSubquery.Data]]] = monocle.Lens[Data, Option[List[StarWarsSubquery.Data]]](_.contacts) {
      v => _ match {
        case s: Data.Human =>
          s.copy(contacts = v)
        case s: Data.Droid =>
          s.copy(contacts = v)
      }
    }
    val human: monocle.Prism[Data, Data.Human] = monocle.macros.GenPrism[Data, Data.Human]
    val droid: monocle.Prism[Data, Data.Droid] = monocle.macros.GenPrism[Data, Data.Droid]
    implicit val eqData: cats.Eq[Data] = cats.Eq.fromUniversalEquals
    implicit val showData: cats.Show[Data] = cats.Show.fromToString
    implicit val jsonDecoderData: io.circe.Decoder[Data] = io.circe.Decoder.instance {
      c => c.downField("__typename").as[String].flatMap {
        case "Human" =>
          io.circe.Decoder[Data.Human].tryDecode(c)
        case "Droid" =>
          io.circe.Decoder[Data.Droid].tryDecode(c)
        case other =>
          Left(io.circe.DecodingFailure("Unexpected __typename [" + other + "] for Data", c.history))
      }
    }
  }
  val dataDecoder: io.circe.Decoder[Data] = Data.jsonDecoderData
}


// format: on
