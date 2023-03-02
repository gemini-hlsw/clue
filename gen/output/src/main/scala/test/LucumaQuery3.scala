// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off

package test

import clue.GraphQLOperation

import clue.ErrorPolicyInfo

object LucumaQuery3 extends GraphQLOperation[LucumaODB] {
  import LucumaODB.Scalars._
  ignoreUnusedImportScalars()
  import LucumaODB.Enums._
  ignoreUnusedImportEnums()
  import LucumaODB.Types._
  ignoreUnusedImportTypes()
  val document = """
      query {
        observations(programId: "p-2", first: 2147483647) {
          nodes {
            id
            observationTarget {
              ... on Target {
                target_id: id
                target_name: name
              }
              ... on Asterism {
                asterism_id: id
                asterism_name: name
              }
            }
          }
        }
      }"""
  case class Variables()
  object Variables {
    implicit val eqVariables: cats.Eq[Variables] = cats.Eq.fromUniversalEquals
    implicit val showVariables: cats.Show[Variables] = cats.Show.fromToString
    implicit val jsonEncoderVariables: io.circe.Encoder[Variables] = io.circe.generic.semiauto.deriveEncoder[Variables].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
  }
  case class Data(val observations: Data.Observations)
  object Data {
    case class Observations(val nodes: List[Data.Observations.Nodes])
    object Observations {
      case class Nodes(val id: ObservationId, val observationTarget: Option[Data.Observations.Nodes.ObservationTarget] = None)
      object Nodes {
        sealed trait ObservationTarget
        object ObservationTarget {
          case class Target(val target_id: TargetId, val target_name: NonEmptyString) extends ObservationTarget()
          object Target {
            val target_id: monocle.Lens[Data.Observations.Nodes.ObservationTarget.Target, TargetId] = monocle.macros.GenLens[Data.Observations.Nodes.ObservationTarget.Target](_.target_id)
            val target_name: monocle.Lens[Data.Observations.Nodes.ObservationTarget.Target, NonEmptyString] = monocle.macros.GenLens[Data.Observations.Nodes.ObservationTarget.Target](_.target_name)
            implicit val eqTarget: cats.Eq[Data.Observations.Nodes.ObservationTarget.Target] = cats.Eq.fromUniversalEquals
            implicit val showTarget: cats.Show[Data.Observations.Nodes.ObservationTarget.Target] = cats.Show.fromToString
            implicit val jsonDecoderTarget: io.circe.Decoder[Data.Observations.Nodes.ObservationTarget.Target] = io.circe.generic.semiauto.deriveDecoder[Data.Observations.Nodes.ObservationTarget.Target]
          }
          case class Asterism(val asterism_id: AsterismId, val asterism_name: Option[NonEmptyString] = None) extends ObservationTarget()
          object Asterism {
            val asterism_id: monocle.Lens[Data.Observations.Nodes.ObservationTarget.Asterism, AsterismId] = monocle.macros.GenLens[Data.Observations.Nodes.ObservationTarget.Asterism](_.asterism_id)
            val asterism_name: monocle.Lens[Data.Observations.Nodes.ObservationTarget.Asterism, Option[NonEmptyString]] = monocle.macros.GenLens[Data.Observations.Nodes.ObservationTarget.Asterism](_.asterism_name)
            implicit val eqAsterism: cats.Eq[Data.Observations.Nodes.ObservationTarget.Asterism] = cats.Eq.fromUniversalEquals
            implicit val showAsterism: cats.Show[Data.Observations.Nodes.ObservationTarget.Asterism] = cats.Show.fromToString
            implicit val jsonDecoderAsterism: io.circe.Decoder[Data.Observations.Nodes.ObservationTarget.Asterism] = io.circe.generic.semiauto.deriveDecoder[Data.Observations.Nodes.ObservationTarget.Asterism]
          }
          implicit val eqObservationTarget: cats.Eq[Data.Observations.Nodes.ObservationTarget] = cats.Eq.fromUniversalEquals
          implicit val showObservationTarget: cats.Show[Data.Observations.Nodes.ObservationTarget] = cats.Show.fromToString
          implicit val jsonDecoderObservationTarget: io.circe.Decoder[Data.Observations.Nodes.ObservationTarget] = List[io.circe.Decoder[Data.Observations.Nodes.ObservationTarget]](io.circe.Decoder[Data.Observations.Nodes.ObservationTarget.Target].asInstanceOf[io.circe.Decoder[Data.Observations.Nodes.ObservationTarget]], io.circe.Decoder[Data.Observations.Nodes.ObservationTarget.Asterism].asInstanceOf[io.circe.Decoder[Data.Observations.Nodes.ObservationTarget]]).reduceLeft(_ or _)
        }
        val id: monocle.Lens[Data.Observations.Nodes, ObservationId] = monocle.macros.GenLens[Data.Observations.Nodes](_.id)
        val observationTarget: monocle.Lens[Data.Observations.Nodes, Option[Data.Observations.Nodes.ObservationTarget]] = monocle.macros.GenLens[Data.Observations.Nodes](_.observationTarget)
        implicit val eqNodes: cats.Eq[Data.Observations.Nodes] = cats.Eq.fromUniversalEquals
        implicit val showNodes: cats.Show[Data.Observations.Nodes] = cats.Show.fromToString
        implicit val jsonDecoderNodes: io.circe.Decoder[Data.Observations.Nodes] = io.circe.generic.semiauto.deriveDecoder[Data.Observations.Nodes]
      }
      val nodes: monocle.Lens[Data.Observations, List[Data.Observations.Nodes]] = monocle.macros.GenLens[Data.Observations](_.nodes)
      implicit val eqObservations: cats.Eq[Data.Observations] = cats.Eq.fromUniversalEquals
      implicit val showObservations: cats.Show[Data.Observations] = cats.Show.fromToString
      implicit val jsonDecoderObservations: io.circe.Decoder[Data.Observations] = io.circe.generic.semiauto.deriveDecoder[Data.Observations]
    }
    val observations: monocle.Lens[Data, Data.Observations] = monocle.macros.GenLens[Data](_.observations)
    implicit val eqData: cats.Eq[Data] = cats.Eq.fromUniversalEquals
    implicit val showData: cats.Show[Data] = cats.Show.fromToString
    implicit val jsonDecoderData: io.circe.Decoder[Data] = io.circe.generic.semiauto.deriveDecoder[Data]
  }
  val varEncoder: io.circe.Encoder[Variables] = Variables.jsonEncoderVariables
  val dataDecoder: io.circe.Decoder[Data] = Data.jsonDecoderData
  def query[F[_], EP]()(implicit client: clue.TransactionalClient[F, LucumaODB], errorPolicyInfo: clue.ErrorPolicy[EP]) = client.request(this)(errorPolicyInfo)(Variables())
  def query_[F[_]]()(implicit client: clue.TransactionalClient[F, LucumaODB]) = client.request_(this)(Variables())
}
// format: on
