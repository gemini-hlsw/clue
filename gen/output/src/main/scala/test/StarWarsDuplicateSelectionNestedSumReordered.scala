// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off

package test

import clue.GraphQLOperation

// The nested polymorphic `friends` is duplicated with its fragments in the opposite order; the
// response shape is identical, so it must merge into one `friends` param typed with the single
// base-level sealed trait `Data.Hero.Friends` (cases `Human`, `Droid`, no `Other` since Character
// has only those two implementors).

object StarWarsDuplicateSelectionNestedSumReordered extends GraphQLOperation[StarWars] {
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
            friends {
              __typename
              name
              ... on Human { homePlanet }
              ... on Droid { primaryFunction }
            }
            ... on Human {
              friends {
                __typename
                name
                ... on Droid { primaryFunction }
                ... on Human { homePlanet }
              }
              homePlanet
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
    sealed trait Hero { val friends: Option[List[Data.Hero.Friends]] }
    object Hero {
      sealed trait Friends { val name: Option[String] }
      object Friends {
        case class Human(override val name: Option[String] = None, val homePlanet: Option[String] = None) extends Friends()
        object Human {
          val name: monocle.Lens[Data.Hero.Friends.Human, Option[String]] = monocle.macros.GenLens[Data.Hero.Friends.Human](_.name)
          val homePlanet: monocle.Lens[Data.Hero.Friends.Human, Option[String]] = monocle.macros.GenLens[Data.Hero.Friends.Human](_.homePlanet)
          implicit val eqHuman: cats.Eq[Data.Hero.Friends.Human] = cats.Eq.fromUniversalEquals
          implicit val showHuman: cats.Show[Data.Hero.Friends.Human] = cats.Show.fromToString
          implicit val jsonDecoderHuman: io.circe.Decoder[Data.Hero.Friends.Human] = io.circe.generic.semiauto.deriveDecoder[Data.Hero.Friends.Human]
        }
        case class Droid(override val name: Option[String] = None, @deprecated("Use 'functions' instead") val primaryFunction: Option[String] = None) extends Friends()
        object Droid {
          val name: monocle.Lens[Data.Hero.Friends.Droid, Option[String]] = monocle.macros.GenLens[Data.Hero.Friends.Droid](_.name)
          @deprecated("Use 'functions' instead") val primaryFunction: monocle.Lens[Data.Hero.Friends.Droid, Option[String]] = monocle.macros.GenLens[Data.Hero.Friends.Droid](_.primaryFunction)
          implicit val eqDroid: cats.Eq[Data.Hero.Friends.Droid] = cats.Eq.fromUniversalEquals
          implicit val showDroid: cats.Show[Data.Hero.Friends.Droid] = cats.Show.fromToString
          implicit val jsonDecoderDroid: io.circe.Decoder[Data.Hero.Friends.Droid] = io.circe.generic.semiauto.deriveDecoder[Data.Hero.Friends.Droid]
        }
        val name: monocle.Lens[Data.Hero.Friends, Option[String]] = monocle.Lens[Data.Hero.Friends, Option[String]](_.name) {
          v => _ match {
            case s: Data.Hero.Friends.Human =>
              s.copy(name = v)
            case s: Data.Hero.Friends.Droid =>
              s.copy(name = v)
          }
        }
        val human: monocle.Prism[Data.Hero.Friends, Data.Hero.Friends.Human] = monocle.macros.GenPrism[Data.Hero.Friends, Data.Hero.Friends.Human]
        val droid: monocle.Prism[Data.Hero.Friends, Data.Hero.Friends.Droid] = monocle.macros.GenPrism[Data.Hero.Friends, Data.Hero.Friends.Droid]
        implicit val eqFriends: cats.Eq[Data.Hero.Friends] = cats.Eq.fromUniversalEquals
        implicit val showFriends: cats.Show[Data.Hero.Friends] = cats.Show.fromToString
        implicit val jsonDecoderFriends: io.circe.Decoder[Data.Hero.Friends] = io.circe.Decoder.instance {
          c => c.downField("__typename").as[String].flatMap {
            case "Human" =>
              io.circe.Decoder[Data.Hero.Friends.Human].tryDecode(c)
            case "Droid" =>
              io.circe.Decoder[Data.Hero.Friends.Droid].tryDecode(c)
            case other =>
              Left(io.circe.DecodingFailure("Unexpected __typename [" + other + "] for Friends", c.history))
          }
        }
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
  class ClientAppliedFP[F[_], P](val client: clue.FetchClientWithPars[F, P, StarWars]) { def query(ep: Episode, modParams: P => P = identity) = client.request(StarWarsDuplicateSelectionNestedSumReordered).withDescriptor("StarWarsDuplicateSelectionNestedSumReordered").withInput(Variables(ep), modParams) }
}
// format: on
