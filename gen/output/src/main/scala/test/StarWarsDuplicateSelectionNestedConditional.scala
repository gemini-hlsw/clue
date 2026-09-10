// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off

package test

import clue.GraphQLOperation

// The nested `id` is selected unconditionally at the base level and again with `@include` inside
// the variant fragment. GraphQL merges them, so `friends` must merge into one param typed with the
// single base-level `Data.Hero.Friends`, non-optional (the base occurrence).

object StarWarsDuplicateSelectionNestedConditional extends GraphQLOperation[StarWars] {
  import StarWars.Scalars._
  ignoreUnusedImportScalars()
  import StarWars.Enums._
  ignoreUnusedImportEnums()
  import StarWars.Types._
  ignoreUnusedImportTypes()
  override val document = gql"""
        query ($$ep: Episode!, $$withId: Boolean!) {
          hero(episode: $$ep) {
            __typename
            friends {
              id
            }
            ... on Human {
              friends {
                id @include(if: $$withId)
              }
              homePlanet
            }
          }
        }
      """
  case class Variables(val ep: Episode, val withId: Boolean)
  object Variables {
    val ep: monocle.Lens[Variables, Episode] = monocle.macros.GenLens[Variables](_.ep)
    val withId: monocle.Lens[Variables, Boolean] = monocle.macros.GenLens[Variables](_.withId)
    implicit val eqVariables: cats.Eq[Variables] = cats.Eq.fromUniversalEquals
    implicit val showVariables: cats.Show[Variables] = cats.Show.fromToString
    implicit val jsonEncoderVariables: io.circe.Encoder.AsObject[Variables] = io.circe.generic.semiauto.deriveEncoder[Variables].mapJsonObject(clue.data.Input.dropIgnores)
  }
  case class Data(val hero: Data.Hero)
  object Data {
    sealed trait Hero { val friends: Option[List[Data.Hero.Friends]] }
    object Hero {
      case class Friends(val id: String)
      object Friends {
        val id: monocle.Iso[Data.Hero.Friends, String] = monocle.Focus[Data.Hero.Friends](_.id)
        implicit val eqFriends: cats.Eq[Data.Hero.Friends] = cats.Eq.fromUniversalEquals
        implicit val showFriends: cats.Show[Data.Hero.Friends] = cats.Show.fromToString
        implicit val jsonDecoderFriends: io.circe.Decoder[Data.Hero.Friends] = io.circe.generic.semiauto.deriveDecoder[Data.Hero.Friends]
      }
      case class Human(override val friends: Option[List[Data.Hero.Friends]] = None, val homePlanet: Option[String] = None) extends Hero()
      object Human {
        val friends: monocle.Lens[Data.Hero.Human, Option[List[Data.Hero.Friends]]] = monocle.macros.GenLens[Data.Hero.Human](_.friends)
        val homePlanet: monocle.Lens[Data.Hero.Human, Option[String]] = monocle.macros.GenLens[Data.Hero.Human](_.homePlanet)
        implicit val eqHuman: cats.Eq[Data.Hero.Human] = cats.Eq.fromUniversalEquals
        implicit val showHuman: cats.Show[Data.Hero.Human] = cats.Show.fromToString
        implicit val jsonDecoderHuman: io.circe.Decoder[Data.Hero.Human] = io.circe.generic.semiauto.deriveDecoder[Data.Hero.Human]
      }
      case class Other(override val friends: Option[List[Data.Hero.Friends]] = None) extends Hero()
      object Other {
        val friends: monocle.Iso[Data.Hero.Other, Option[List[Data.Hero.Friends]]] = monocle.Focus[Data.Hero.Other](_.friends)
        implicit val eqOther: cats.Eq[Data.Hero.Other] = cats.Eq.fromUniversalEquals
        implicit val showOther: cats.Show[Data.Hero.Other] = cats.Show.fromToString
        implicit val jsonDecoderOther: io.circe.Decoder[Data.Hero.Other] = io.circe.generic.semiauto.deriveDecoder[Data.Hero.Other]
      }
      val friends: monocle.Lens[Data.Hero, Option[List[Data.Hero.Friends]]] = monocle.Lens[Data.Hero, Option[List[Data.Hero.Friends]]](_.friends) {
        v => _ match {
          case s: Data.Hero.Human =>
            s.copy(friends = v)
          case s: Data.Hero.Other =>
            s.copy(friends = v)
        }
      }
      val human: monocle.Prism[Data.Hero, Data.Hero.Human] = monocle.macros.GenPrism[Data.Hero, Data.Hero.Human]
      val other: monocle.Prism[Data.Hero, Data.Hero.Other] = monocle.macros.GenPrism[Data.Hero, Data.Hero.Other]
      implicit val eqHero: cats.Eq[Data.Hero] = cats.Eq.fromUniversalEquals
      implicit val showHero: cats.Show[Data.Hero] = cats.Show.fromToString
      implicit val jsonDecoderHero: io.circe.Decoder[Data.Hero] = io.circe.Decoder.instance {
        c => c.downField("__typename").as[String].flatMap {
          case "Human" =>
            io.circe.Decoder[Data.Hero.Human].tryDecode(c)
          case _ =>
            io.circe.Decoder[Data.Hero.Other].tryDecode(c)
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
  class ClientAppliedFP[F[_], P](val client: clue.FetchClientWithPars[F, P, StarWars]) { def query(ep: Episode, withId: Boolean, modParams: P => P = identity) = client.request(StarWarsDuplicateSelectionNestedConditional).withDescriptor("StarWarsDuplicateSelectionNestedConditional").withInput(Variables(ep, withId), modParams) }
}
// format: on
