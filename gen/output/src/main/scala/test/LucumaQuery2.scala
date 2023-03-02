// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off

package test

import clue.GraphQLOperation

import clue.ErrorPolicyInfo

object LucumaQuery2 extends GraphQLOperation[LucumaODB] {
  import LucumaODB.Scalars._
  ignoreUnusedImportScalars()
  import LucumaODB.Enums._
  ignoreUnusedImportEnums()
  import LucumaODB.Types._
  ignoreUnusedImportTypes()
  val document = """
      query Program {
        program(programId: "p-2") {
          id
          name
          targets(includeDeleted: true, first: 100) {
            nodes {
              id
              name
              tracking {
                ... on Sidereal {
                  epoch
                }
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
  case class Data(val program: Option[Data.Program] = None)
  object Data {
    case class Program(val id: ProgramId, val name: Option[NonEmptyString] = None, val targets: Data.Program.Targets)
    object Program {
      case class Targets(val nodes: List[Data.Program.Targets.Nodes])
      object Targets {
        case class Nodes(val id: TargetId, val name: NonEmptyString, val tracking: Data.Program.Targets.Nodes.Tracking)
        object Nodes {
          case class Tracking(val epoch: EpochString)
          object Tracking {
            val epoch: monocle.Lens[Data.Program.Targets.Nodes.Tracking, EpochString] = monocle.macros.GenLens[Data.Program.Targets.Nodes.Tracking](_.epoch)
            implicit val eqTracking: cats.Eq[Data.Program.Targets.Nodes.Tracking] = cats.Eq.fromUniversalEquals
            implicit val showTracking: cats.Show[Data.Program.Targets.Nodes.Tracking] = cats.Show.fromToString
            implicit val jsonDecoderTracking: io.circe.Decoder[Data.Program.Targets.Nodes.Tracking] = io.circe.generic.semiauto.deriveDecoder[Data.Program.Targets.Nodes.Tracking]
          }
          val id: monocle.Lens[Data.Program.Targets.Nodes, TargetId] = monocle.macros.GenLens[Data.Program.Targets.Nodes](_.id)
          val name: monocle.Lens[Data.Program.Targets.Nodes, NonEmptyString] = monocle.macros.GenLens[Data.Program.Targets.Nodes](_.name)
          val tracking: monocle.Lens[Data.Program.Targets.Nodes, Data.Program.Targets.Nodes.Tracking] = monocle.macros.GenLens[Data.Program.Targets.Nodes](_.tracking)
          implicit val eqNodes: cats.Eq[Data.Program.Targets.Nodes] = cats.Eq.fromUniversalEquals
          implicit val showNodes: cats.Show[Data.Program.Targets.Nodes] = cats.Show.fromToString
          implicit val jsonDecoderNodes: io.circe.Decoder[Data.Program.Targets.Nodes] = io.circe.generic.semiauto.deriveDecoder[Data.Program.Targets.Nodes]
        }
        val nodes: monocle.Lens[Data.Program.Targets, List[Data.Program.Targets.Nodes]] = monocle.macros.GenLens[Data.Program.Targets](_.nodes)
        implicit val eqTargets: cats.Eq[Data.Program.Targets] = cats.Eq.fromUniversalEquals
        implicit val showTargets: cats.Show[Data.Program.Targets] = cats.Show.fromToString
        implicit val jsonDecoderTargets: io.circe.Decoder[Data.Program.Targets] = io.circe.generic.semiauto.deriveDecoder[Data.Program.Targets]
      }
      val id: monocle.Lens[Data.Program, ProgramId] = monocle.macros.GenLens[Data.Program](_.id)
      val name: monocle.Lens[Data.Program, Option[NonEmptyString]] = monocle.macros.GenLens[Data.Program](_.name)
      val targets: monocle.Lens[Data.Program, Data.Program.Targets] = monocle.macros.GenLens[Data.Program](_.targets)
      implicit val eqProgram: cats.Eq[Data.Program] = cats.Eq.fromUniversalEquals
      implicit val showProgram: cats.Show[Data.Program] = cats.Show.fromToString
      implicit val jsonDecoderProgram: io.circe.Decoder[Data.Program] = io.circe.generic.semiauto.deriveDecoder[Data.Program]
    }
    val program: monocle.Lens[Data, Option[Data.Program]] = monocle.macros.GenLens[Data](_.program)
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
