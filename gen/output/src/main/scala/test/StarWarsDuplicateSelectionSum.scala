// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off

package test

import clue.GraphQLOperation

// `name` is selected at the base level AND again inside both variant fragments (same response
// name): GraphQL merges them, so each variant's generated case class must carry `name` once (as
// `override val`), not twice. `Human` and `Droid` are Character's only implementors, so no `Other`.

object StarWarsDuplicateSelectionSum extends GraphQLOperation[StarWars] {
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
            ... on Human {
              name
              homePlanet
            }
            ... on Droid {
              name
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
      case class Human(override val name: Option[String] = None, val homePlanet: Option[String] = None) extends Hero()
      object Human {
        val name: monocle.Lens[Data.Hero.Human, Option[String]] = monocle.macros.GenLens[Data.Hero.Human](_.name)
        val homePlanet: monocle.Lens[Data.Hero.Human, Option[String]] = monocle.macros.GenLens[Data.Hero.Human](_.homePlanet)
        implicit val eqHuman: cats.Eq[Data.Hero.Human] = cats.Eq.fromUniversalEquals
        implicit val showHuman: cats.Show[Data.Hero.Human] = cats.Show.fromToString
        implicit val jsonDecoderHuman: io.circe.Decoder[Data.Hero.Human] = io.circe.generic.semiauto.deriveDecoder[Data.Hero.Human]
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
          case s: Data.Hero.Human =>
            s.copy(name = v)
          case s: Data.Hero.Droid =>
            s.copy(name = v)
        }
      }
      val human: monocle.Prism[Data.Hero, Data.Hero.Human] = monocle.macros.GenPrism[Data.Hero, Data.Hero.Human]
      val droid: monocle.Prism[Data.Hero, Data.Hero.Droid] = monocle.macros.GenPrism[Data.Hero, Data.Hero.Droid]
      implicit val eqHero: cats.Eq[Data.Hero] = cats.Eq.fromUniversalEquals
      implicit val showHero: cats.Show[Data.Hero] = cats.Show.fromToString
      implicit val jsonDecoderHero: io.circe.Decoder[Data.Hero] = io.circe.Decoder.instance {
        c => c.downField("__typename").as[String].flatMap {
          case "Human" =>
            io.circe.Decoder[Data.Hero.Human].tryDecode(c)
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
  class ClientAppliedFP[F[_], P](val client: clue.FetchClientWithPars[F, P, StarWars]) { def query(ep: Episode, modParams: P => P = identity) = client.request(StarWarsDuplicateSelectionSum).withDescriptor("StarWarsDuplicateSelectionSum").withInput(Variables(ep), modParams) }
}
// format: on
