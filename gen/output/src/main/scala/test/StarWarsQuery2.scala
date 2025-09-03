// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off

package test

import clue.GraphQLOperation
import japgolly.scalajs.react.Dummy._

object Wrapper extends Something {
  
  object StarWarsQuery2 extends GraphQLOperation[StarWars] {
    import StarWars.Scalars._
    ignoreUnusedImportScalars()
    import StarWars.Enums._
    ignoreUnusedImportEnums()
    import StarWars.Types._
    ignoreUnusedImportTypes()
    override val document: String = """
          fragment fields on Character {
            id
            name
            ... on Human {
              homePlanet
            }
            friends {
              name
            }
            ... on Droid {
              primaryFunction
            }
          }
          query ($charId: ID!) {
            character(id: $charId) {
              ...fields
            }
          }
        """
    case class Variables(val charId: String)
    object Variables {
      val charId: monocle.Lens[Variables, String] = monocle.macros.GenLens[Variables](_.charId)
      implicit val eqVariables: cats.Eq[Variables] = cats.Eq.fromUniversalEquals
      implicit val showVariables: cats.Show[Variables] = cats.Show.fromToString
      implicit val jsonEncoderVariables: io.circe.Encoder.AsObject[Variables] = io.circe.generic.semiauto.deriveEncoder[Variables].mapJsonObject(clue.data.Input.dropIgnores)
    }
    case class Data(val character: Option[Data.Character] = None)
    object Data {
      sealed trait Character {
        val id: String
        val name: Option[String]
        val friends: Option[List[Data.Character.Friends]]
      }
      object Character {
        case class Friends(val name: Option[String] = None)
        object Friends {
          val name: monocle.Lens[Data.Character.Friends, Option[String]] = monocle.macros.GenLens[Data.Character.Friends](_.name)
          implicit val eqFriends: cats.Eq[Data.Character.Friends] = cats.Eq.fromUniversalEquals
          implicit val showFriends: cats.Show[Data.Character.Friends] = cats.Show.fromToString
          implicit val reuseFriends: japgolly.scalajs.react.Reusability[Data.Character.Friends] = {
            japgolly.scalajs.react.Reusability.derive
          }
          implicit val jsonDecoderFriends: io.circe.Decoder[Data.Character.Friends] = io.circe.generic.semiauto.deriveDecoder[Data.Character.Friends]
        }
        case class Human(override val id: String, override val name: Option[String] = None, val homePlanet: Option[String] = None, override val friends: Option[List[Data.Character.Friends]] = None) extends Character()
        object Human {
          val id: monocle.Lens[Data.Character.Human, String] = monocle.macros.GenLens[Data.Character.Human](_.id)
          val name: monocle.Lens[Data.Character.Human, Option[String]] = monocle.macros.GenLens[Data.Character.Human](_.name)
          val homePlanet: monocle.Lens[Data.Character.Human, Option[String]] = monocle.macros.GenLens[Data.Character.Human](_.homePlanet)
          val friends: monocle.Lens[Data.Character.Human, Option[List[Data.Character.Friends]]] = monocle.macros.GenLens[Data.Character.Human](_.friends)
          implicit val eqHuman: cats.Eq[Data.Character.Human] = cats.Eq.fromUniversalEquals
          implicit val showHuman: cats.Show[Data.Character.Human] = cats.Show.fromToString
          implicit val reuseHuman: japgolly.scalajs.react.Reusability[Data.Character.Human] = {
            japgolly.scalajs.react.Reusability.derive
          }
          implicit val jsonDecoderHuman: io.circe.Decoder[Data.Character.Human] = io.circe.generic.semiauto.deriveDecoder[Data.Character.Human]
        }
        case class Droid(override val id: String, override val name: Option[String] = None, override val friends: Option[List[Data.Character.Friends]] = None, @deprecated("Use 'functions' instead") val primaryFunction: Option[String] = None) extends Character()
        object Droid {
          val id: monocle.Lens[Data.Character.Droid, String] = monocle.macros.GenLens[Data.Character.Droid](_.id)
          val name: monocle.Lens[Data.Character.Droid, Option[String]] = monocle.macros.GenLens[Data.Character.Droid](_.name)
          val friends: monocle.Lens[Data.Character.Droid, Option[List[Data.Character.Friends]]] = monocle.macros.GenLens[Data.Character.Droid](_.friends)
          val primaryFunction: monocle.Lens[Data.Character.Droid, Option[String]] = monocle.macros.GenLens[Data.Character.Droid](_.primaryFunction)
          implicit val eqDroid: cats.Eq[Data.Character.Droid] = cats.Eq.fromUniversalEquals
          implicit val showDroid: cats.Show[Data.Character.Droid] = cats.Show.fromToString
          implicit val reuseDroid: japgolly.scalajs.react.Reusability[Data.Character.Droid] = {
            japgolly.scalajs.react.Reusability.derive
          }
          implicit val jsonDecoderDroid: io.circe.Decoder[Data.Character.Droid] = io.circe.generic.semiauto.deriveDecoder[Data.Character.Droid]
        }
        val id: monocle.Lens[Data.Character, String] = monocle.Lens[Data.Character, String](_.id) {
          v => _ match {
            case s: Data.Character.Human =>
              s.copy(id = v)
            case s: Data.Character.Droid =>
              s.copy(id = v)
          }
        }
        val name: monocle.Lens[Data.Character, Option[String]] = monocle.Lens[Data.Character, Option[String]](_.name) {
          v => _ match {
            case s: Data.Character.Human =>
              s.copy(name = v)
            case s: Data.Character.Droid =>
              s.copy(name = v)
          }
        }
        val friends: monocle.Lens[Data.Character, Option[List[Data.Character.Friends]]] = monocle.Lens[Data.Character, Option[List[Data.Character.Friends]]](_.friends) {
          v => _ match {
            case s: Data.Character.Human =>
              s.copy(friends = v)
            case s: Data.Character.Droid =>
              s.copy(friends = v)
          }
        }
        implicit val eqCharacter: cats.Eq[Data.Character] = cats.Eq.fromUniversalEquals
        implicit val showCharacter: cats.Show[Data.Character] = cats.Show.fromToString
        implicit val reuseCharacter: japgolly.scalajs.react.Reusability[Data.Character] = {
          japgolly.scalajs.react.Reusability.derive
        }
        implicit val jsonDecoderCharacter: io.circe.Decoder[Data.Character] = List[io.circe.Decoder[Data.Character]](io.circe.Decoder[Data.Character.Human].asInstanceOf[io.circe.Decoder[Data.Character]], io.circe.Decoder[Data.Character.Droid].asInstanceOf[io.circe.Decoder[Data.Character]]).reduceLeft(_ or _)
      }
      val character: monocle.Lens[Data, Option[Data.Character]] = monocle.macros.GenLens[Data](_.character)
      implicit val eqData: cats.Eq[Data] = cats.Eq.fromUniversalEquals
      implicit val showData: cats.Show[Data] = cats.Show.fromToString
      implicit val reuseData: japgolly.scalajs.react.Reusability[Data] = {
        japgolly.scalajs.react.Reusability.derive
      }
      implicit val jsonDecoderData: io.circe.Decoder[Data] = io.circe.generic.semiauto.deriveDecoder[Data]
    }
    val varEncoder: io.circe.Encoder.AsObject[Variables] = Variables.jsonEncoderVariables
    val dataDecoder: io.circe.Decoder[Data] = Data.jsonDecoderData
    def apply[F[_]]: clue.ClientAppliedF[F, StarWars, ClientAppliedFP] = new clue.ClientAppliedF[F, StarWars, ClientAppliedFP] { def applyP[P](client: clue.FetchClientWithPars[F, P, StarWars]) = new ClientAppliedFP(client) }
    class ClientAppliedFP[F[_], P](val client: clue.FetchClientWithPars[F, P, StarWars]) { def query(charId: String, modParams: P => P = identity) = client.request(StarWarsQuery2).withInput(Variables(charId), modParams) }
  }
}
// format: on
