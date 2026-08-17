// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off

package test

import clue.GraphQLOperation


object StarWarsInclude extends GraphQLOperation[StarWars] {
  import StarWars.Scalars._
  ignoreUnusedImportScalars()
  import StarWars.Enums._
  ignoreUnusedImportEnums()
  import StarWars.Types._
  ignoreUnusedImportTypes()
  override val document = gql"""
        query ($$humanId: ID!, $$skipId: Boolean!, $$withName: Boolean!) {
          human(id: $$humanId) {
            id @skip(if: $$skipId)
            name @include(if: $$withName)
            homePlanet
          }
        }
      """
  case class Variables(val humanId: String, val skipId: Boolean, val withName: Boolean)
  object Variables {
    val humanId: monocle.Lens[Variables, String] = monocle.macros.GenLens[Variables](_.humanId)
    val skipId: monocle.Lens[Variables, Boolean] = monocle.macros.GenLens[Variables](_.skipId)
    val withName: monocle.Lens[Variables, Boolean] = monocle.macros.GenLens[Variables](_.withName)
    implicit val eqVariables: cats.Eq[Variables] = cats.Eq.fromUniversalEquals
    implicit val showVariables: cats.Show[Variables] = cats.Show.fromToString
    implicit val jsonEncoderVariables: io.circe.Encoder.AsObject[Variables] = io.circe.generic.semiauto.deriveEncoder[Variables].mapJsonObject(clue.data.Input.dropIgnores)
  }
  case class Data(val human: Option[Data.Human] = None)
  object Data {
    case class Human(val id: Option[String] = None, val name: Option[String] = None, val homePlanet: Option[String] = None)
    object Human {
      val id: monocle.Lens[Data.Human, Option[String]] = monocle.macros.GenLens[Data.Human](_.id)
      val name: monocle.Lens[Data.Human, Option[String]] = monocle.macros.GenLens[Data.Human](_.name)
      val homePlanet: monocle.Lens[Data.Human, Option[String]] = monocle.macros.GenLens[Data.Human](_.homePlanet)
      implicit val eqHuman: cats.Eq[Data.Human] = cats.Eq.fromUniversalEquals
      implicit val showHuman: cats.Show[Data.Human] = cats.Show.fromToString
      implicit val jsonDecoderHuman: io.circe.Decoder[Data.Human] = io.circe.generic.semiauto.deriveDecoder[Data.Human]
    }
    val human: monocle.Iso[Data, Option[Data.Human]] = monocle.Focus[Data](_.human)
    implicit val eqData: cats.Eq[Data] = cats.Eq.fromUniversalEquals
    implicit val showData: cats.Show[Data] = cats.Show.fromToString
    implicit val jsonDecoderData: io.circe.Decoder[Data] = io.circe.generic.semiauto.deriveDecoder[Data]
  }
  val varEncoder: io.circe.Encoder.AsObject[Variables] = Variables.jsonEncoderVariables
  val dataDecoder: io.circe.Decoder[Data] = Data.jsonDecoderData
  def apply[F[_]]: clue.ClientAppliedF[F, StarWars, ClientAppliedFP] = new clue.ClientAppliedF[F, StarWars, ClientAppliedFP] { def applyP[P](client: clue.FetchClientWithPars[F, P, StarWars]) = new ClientAppliedFP(client) }
  class ClientAppliedFP[F[_], P](val client: clue.FetchClientWithPars[F, P, StarWars]) { def query(humanId: String, skipId: Boolean, withName: Boolean, modParams: P => P = identity) = client.request(StarWarsInclude).withDescriptor("StarWarsInclude").withInput(Variables(humanId, skipId, withName), modParams) }
}
// format: on
