// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off

package test

import clue.GraphQLOperation

// A fragment on an interface (`Pilot`, implemented only by `Human`) plus one on `Droid`, so both
// implementors of `Character` are covered (no `Other`): `__typename` in a response is always the
// concrete object type name (`Human`), never the interface name, so the decoder must map every
// concrete implementor to the `Pilot` instance, not match on `"Pilot"` itself.

object StarWarsInterfaceSubtype extends GraphQLOperation[StarWars] {
  import StarWars.Scalars._
  ignoreUnusedImportScalars()
  import StarWars.Enums._
  ignoreUnusedImportEnums()
  import StarWars.Types._
  ignoreUnusedImportTypes()
  override val document = gql"""
        query ($$ep: Episode!) {
          hero(episode: $$ep) {
            __typename
            name
            ... on Pilot {
              vehicle
            }
            ... on Droid {
              primaryFunction
            }
          }
        }
      """
  case class Variables(val ep: Episode)
  object Variables {
    val ep: monocle.Iso[Variables, Episode] = monocle.Focus[Variables](_.ep)
    implicit val eqVariables: cats.Eq[Variables] = cats.Eq.fromUniversalEquals
    implicit val showVariables: cats.Show[Variables] = cats.Show.fromToString
    implicit val jsonEncoderVariables: io.circe.Encoder.AsObject[Variables] = io.circe.generic.semiauto.deriveEncoder[Variables].mapJsonObject(clue.data.Input.dropIgnores)
  }
  case class Data(val hero: Data.Hero)
  object Data {
    sealed trait Hero { val name: Option[String] }
    object Hero {
      case class Pilot(override val name: Option[String] = None, val vehicle: Option[String] = None) extends Hero()
      object Pilot {
        val name: monocle.Lens[Data.Hero.Pilot, Option[String]] = monocle.macros.GenLens[Data.Hero.Pilot](_.name)
        val vehicle: monocle.Lens[Data.Hero.Pilot, Option[String]] = monocle.macros.GenLens[Data.Hero.Pilot](_.vehicle)
        implicit val eqPilot: cats.Eq[Data.Hero.Pilot] = cats.Eq.fromUniversalEquals
        implicit val showPilot: cats.Show[Data.Hero.Pilot] = cats.Show.fromToString
        implicit val jsonDecoderPilot: io.circe.Decoder[Data.Hero.Pilot] = io.circe.generic.semiauto.deriveDecoder[Data.Hero.Pilot]
      }
      case class Droid(override val name: Option[String] = None, @deprecated("Use 'functions' instead") val primaryFunction: Option[String] = None) extends Hero()
      object Droid {
        val name: monocle.Lens[Data.Hero.Droid, Option[String]] = monocle.macros.GenLens[Data.Hero.Droid](_.name)
        @deprecated("Use 'functions' instead") val primaryFunction: monocle.Lens[Data.Hero.Droid, Option[String]] = monocle.macros.GenLens[Data.Hero.Droid](_.primaryFunction)
        implicit val eqDroid: cats.Eq[Data.Hero.Droid] = cats.Eq.fromUniversalEquals
        implicit val showDroid: cats.Show[Data.Hero.Droid] = cats.Show.fromToString
        implicit val jsonDecoderDroid: io.circe.Decoder[Data.Hero.Droid] = io.circe.generic.semiauto.deriveDecoder[Data.Hero.Droid]
      }
      val name: monocle.Lens[Data.Hero, Option[String]] = monocle.Lens[Data.Hero, Option[String]](_.name) {
        v => _ match {
          case s: Data.Hero.Pilot =>
            s.copy(name = v)
          case s: Data.Hero.Droid =>
            s.copy(name = v)
        }
      }
      val pilot: monocle.Prism[Data.Hero, Data.Hero.Pilot] = monocle.macros.GenPrism[Data.Hero, Data.Hero.Pilot]
      val droid: monocle.Prism[Data.Hero, Data.Hero.Droid] = monocle.macros.GenPrism[Data.Hero, Data.Hero.Droid]
      implicit val eqHero: cats.Eq[Data.Hero] = cats.Eq.fromUniversalEquals
      implicit val showHero: cats.Show[Data.Hero] = cats.Show.fromToString
      implicit val jsonDecoderHero: io.circe.Decoder[Data.Hero] = io.circe.Decoder.instance {
        c => c.downField("__typename").as[String].flatMap {
          case "Human" =>
            io.circe.Decoder[Data.Hero.Pilot].tryDecode(c)
          case "Droid" =>
            io.circe.Decoder[Data.Hero.Droid].tryDecode(c)
          case other =>
            Left(io.circe.DecodingFailure("Unexpected __typename [" + other + "] for Hero", c.history))
        }
      }
    }
    val hero: monocle.Iso[Data, Data.Hero] = monocle.Focus[Data](_.hero)
    implicit val eqData: cats.Eq[Data] = cats.Eq.fromUniversalEquals
    implicit val showData: cats.Show[Data] = cats.Show.fromToString
    implicit val jsonDecoderData: io.circe.Decoder[Data] = io.circe.generic.semiauto.deriveDecoder[Data]
  }
  val varEncoder: io.circe.Encoder.AsObject[Variables] = Variables.jsonEncoderVariables
  val dataDecoder: io.circe.Decoder[Data] = Data.jsonDecoderData
  def apply[F[_]]: clue.ClientAppliedF[F, StarWars, ClientAppliedFP] = new clue.ClientAppliedF[F, StarWars, ClientAppliedFP] { def applyP[P](client: clue.FetchClientWithPars[F, P, StarWars]) = new ClientAppliedFP(client) }
  class ClientAppliedFP[F[_], P](val client: clue.FetchClientWithPars[F, P, StarWars]) { def query(ep: Episode, modParams: P => P = identity) = client.request(StarWarsInterfaceSubtype).withDescriptor("StarWarsInterfaceSubtype").withInput(Variables(ep), modParams) }
}
// format: on
