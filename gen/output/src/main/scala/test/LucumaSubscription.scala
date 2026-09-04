// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off

package test

import clue.GraphQLOperation


object LucumaSubscription extends GraphQLOperation[LucumaODB] {
  import LucumaODB.Scalars._
  ignoreUnusedImportScalars()
  import LucumaODB.Enums._
  ignoreUnusedImportEnums()
  import LucumaODB.Types._
  ignoreUnusedImportTypes()
  val document = gql"""
      subscription AsterismEdit($$programId: ProgramId) {
        asterismEdit(programId: $$programId) {
          editType
          value {
            id
            name
          }
        }
      }"""
  case class Variables(val programId: clue.data.Input[ProgramId] = clue.data.Ignore)
  object Variables {
    val programId: monocle.Iso[Variables, clue.data.Input[ProgramId]] = monocle.Focus[Variables](_.programId)
    implicit val eqVariables: cats.Eq[Variables] = cats.Eq.fromUniversalEquals
    implicit val showVariables: cats.Show[Variables] = cats.Show.fromToString
    implicit val jsonEncoderVariables: io.circe.Encoder.AsObject[Variables] = io.circe.generic.semiauto.deriveEncoder[Variables].mapJsonObject(clue.data.Input.dropIgnores)
  }
  case class Data(val asterismEdit: Data.AsterismEdit)
  object Data {
    case class AsterismEdit(val editType: EditType, val value: Data.AsterismEdit.Value)
    object AsterismEdit {
      case class Value(val id: AsterismId, val name: Option[NonEmptyString] = None)
      object Value {
        val id: monocle.Lens[Data.AsterismEdit.Value, AsterismId] = monocle.macros.GenLens[Data.AsterismEdit.Value](_.id)
        val name: monocle.Lens[Data.AsterismEdit.Value, Option[NonEmptyString]] = monocle.macros.GenLens[Data.AsterismEdit.Value](_.name)
        implicit val eqValue: cats.Eq[Data.AsterismEdit.Value] = cats.Eq.fromUniversalEquals
        implicit val showValue: cats.Show[Data.AsterismEdit.Value] = cats.Show.fromToString
        implicit val jsonDecoderValue: io.circe.Decoder[Data.AsterismEdit.Value] = io.circe.generic.semiauto.deriveDecoder[Data.AsterismEdit.Value]
      }
      val editType: monocle.Lens[Data.AsterismEdit, EditType] = monocle.macros.GenLens[Data.AsterismEdit](_.editType)
      val value: monocle.Lens[Data.AsterismEdit, Data.AsterismEdit.Value] = monocle.macros.GenLens[Data.AsterismEdit](_.value)
      implicit val eqAsterismEdit: cats.Eq[Data.AsterismEdit] = cats.Eq.fromUniversalEquals
      implicit val showAsterismEdit: cats.Show[Data.AsterismEdit] = cats.Show.fromToString
      implicit val jsonDecoderAsterismEdit: io.circe.Decoder[Data.AsterismEdit] = io.circe.generic.semiauto.deriveDecoder[Data.AsterismEdit]
    }
    val asterismEdit: monocle.Iso[Data, Data.AsterismEdit] = monocle.Focus[Data](_.asterismEdit)
    implicit val eqData: cats.Eq[Data] = cats.Eq.fromUniversalEquals
    implicit val showData: cats.Show[Data] = cats.Show.fromToString
    implicit val jsonDecoderData: io.circe.Decoder[Data] = io.circe.generic.semiauto.deriveDecoder[Data]
  }
  val varEncoder: io.circe.Encoder.AsObject[Variables] = Variables.jsonEncoderVariables
  val dataDecoder: io.circe.Decoder[Data] = Data.jsonDecoderData
  def subscribe[F[_]](programId: clue.data.Input[ProgramId] = clue.data.Ignore)(implicit client: clue.StreamingClient[F, LucumaODB]) = client.subscribe(this).withDescriptor("LucumaSubscription").withInput(Variables(programId))
}
// format: on
