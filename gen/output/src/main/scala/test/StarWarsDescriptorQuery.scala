// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off

package test

import clue.GraphQLDocument
import clue.GraphQLOperation
import clue.gql


object StarWarsDescriptorQuery extends GraphQLOperation[StarWars] {
  import StarWars.Scalars._
  ignoreUnusedImportScalars()
  import StarWars.Enums._
  ignoreUnusedImportEnums()
  import StarWars.Types._
  ignoreUnusedImportTypes()
  override val document: GraphQLDocument = gql"""
        query ($$charId: ID!) {
          character(id: $$charId) {
            id
            name
          }
        }
      """
  case class Variables(val charId: String)
  object Variables {
    val charId: monocle.Iso[Variables, String] = monocle.Focus[Variables](_.charId)
    implicit val eqVariables: cats.Eq[Variables] = cats.Eq.fromUniversalEquals
    implicit val showVariables: cats.Show[Variables] = cats.Show.fromToString
    implicit val jsonEncoderVariables: io.circe.Encoder.AsObject[Variables] = io.circe.generic.semiauto.deriveEncoder[Variables].mapJsonObject(clue.data.Input.dropIgnores)
  }
  case class Data(val character: Option[Data.Character] = None)
  object Data {
    case class Character(val id: String, val name: Option[String] = None)
    object Character {
      val id: monocle.Lens[Data.Character, String] = monocle.macros.GenLens[Data.Character](_.id)
      val name: monocle.Lens[Data.Character, Option[String]] = monocle.macros.GenLens[Data.Character](_.name)
      implicit val eqCharacter: cats.Eq[Data.Character] = cats.Eq.fromUniversalEquals
      implicit val showCharacter: cats.Show[Data.Character] = cats.Show.fromToString
      implicit val jsonDecoderCharacter: io.circe.Decoder[Data.Character] = io.circe.generic.semiauto.deriveDecoder[Data.Character]
    }
    val character: monocle.Iso[Data, Option[Data.Character]] = monocle.Focus[Data](_.character)
    implicit val eqData: cats.Eq[Data] = cats.Eq.fromUniversalEquals
    implicit val showData: cats.Show[Data] = cats.Show.fromToString
    implicit val jsonDecoderData: io.circe.Decoder[Data] = io.circe.generic.semiauto.deriveDecoder[Data]
  }
  val varEncoder: io.circe.Encoder.AsObject[Variables] = Variables.jsonEncoderVariables
  val dataDecoder: io.circe.Decoder[Data] = Data.jsonDecoderData
  def apply[F[_]]: clue.ClientAppliedF[F, StarWars, ClientAppliedFP] = new clue.ClientAppliedF[F, StarWars, ClientAppliedFP] { def applyP[P](client: clue.FetchClientWithPars[F, P, StarWars]) = new ClientAppliedFP(client) }
  class ClientAppliedFP[F[_], P](val client: clue.FetchClientWithPars[F, P, StarWars]) { def query(charId: String, modParams: P => P = identity) = client.request(StarWarsDescriptorQuery).withInput(Variables(charId), modParams) }
}
// format: on
