// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off

package test

import clue.GraphQLOperation
import clue.gql


object LucumaMutation extends GraphQLOperation[LucumaODB] {
  import LucumaODB.Scalars._
  ignoreUnusedImportScalars()
  import LucumaODB.Enums._
  ignoreUnusedImportEnums()
  import LucumaODB.Types._
  ignoreUnusedImportTypes()
  val document = gql"""
      mutation DeleteAsterism($$asterismId: AsterismId!) {
        deleteAsterism(asterismId: $$asterismId) {
          id
          existence
        }
      }"""
  case class Variables(val asterismId: AsterismId)
  object Variables {
    val asterismId: monocle.Iso[Variables, AsterismId] = monocle.Focus[Variables](_.asterismId)
    implicit val eqVariables: cats.Eq[Variables] = cats.Eq.fromUniversalEquals
    implicit val showVariables: cats.Show[Variables] = cats.Show.fromToString
    implicit val jsonEncoderVariables: io.circe.Encoder.AsObject[Variables] = io.circe.generic.semiauto.deriveEncoder[Variables].mapJsonObject(clue.data.Input.dropIgnores)
  }
  case class Data(val deleteAsterism: Data.DeleteAsterism)
  object Data {
    case class DeleteAsterism(val id: AsterismId, val existence: Existence)
    object DeleteAsterism {
      val id: monocle.Lens[Data.DeleteAsterism, AsterismId] = monocle.macros.GenLens[Data.DeleteAsterism](_.id)
      val existence: monocle.Lens[Data.DeleteAsterism, Existence] = monocle.macros.GenLens[Data.DeleteAsterism](_.existence)
      implicit val eqDeleteAsterism: cats.Eq[Data.DeleteAsterism] = cats.Eq.fromUniversalEquals
      implicit val showDeleteAsterism: cats.Show[Data.DeleteAsterism] = cats.Show.fromToString
      implicit val jsonDecoderDeleteAsterism: io.circe.Decoder[Data.DeleteAsterism] = io.circe.generic.semiauto.deriveDecoder[Data.DeleteAsterism]
    }
    val deleteAsterism: monocle.Iso[Data, Data.DeleteAsterism] = monocle.Focus[Data](_.deleteAsterism)
    implicit val eqData: cats.Eq[Data] = cats.Eq.fromUniversalEquals
    implicit val showData: cats.Show[Data] = cats.Show.fromToString
    implicit val jsonDecoderData: io.circe.Decoder[Data] = io.circe.generic.semiauto.deriveDecoder[Data]
  }
  val varEncoder: io.circe.Encoder.AsObject[Variables] = Variables.jsonEncoderVariables
  val dataDecoder: io.circe.Decoder[Data] = Data.jsonDecoderData
  def apply[F[_]]: clue.ClientAppliedF[F, LucumaODB, ClientAppliedFP] = new clue.ClientAppliedF[F, LucumaODB, ClientAppliedFP] { def applyP[P](client: clue.FetchClientWithPars[F, P, LucumaODB]) = new ClientAppliedFP(client) }
  class ClientAppliedFP[F[_], P](val client: clue.FetchClientWithPars[F, P, LucumaODB]) { def execute(asterismId: AsterismId, modParams: P => P = identity) = client.request(LucumaMutation).withDescriptor("LucumaMutation").withInput(Variables(asterismId), modParams) }
}
// format: on
