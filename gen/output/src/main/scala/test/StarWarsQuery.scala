// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off

package test

import clue.GraphQLOperation
import test.StarWars


object StarWarsQuery extends GraphQLOperation[StarWars] {
  import StarWars.Scalars._
  ignoreUnusedImportScalars()
  import StarWars.Enums._
  ignoreUnusedImportEnums()
  import StarWars.Types._
  ignoreUnusedImportTypes()
  override val document: String = """
        query ($charId: ID!) {
          character(id: $charId) {
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
        }
      """
  case class Variables(val charId: String)
  object Variables {
    val charId: monocle.Lens[Variables, String] = monocle.macros.GenLens[Variables](_.charId)
    implicit val eqVariables: cats.Eq[Variables] = cats.Eq.fromUniversalEquals
    implicit val showVariables: cats.Show[Variables] = cats.Show.fromToString
    implicit val jsonEncoderVariables: io.circe.Encoder[Variables] = io.circe.generic.semiauto.deriveEncoder[Variables].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
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
        implicit val jsonDecoderHuman: io.circe.Decoder[Data.Character.Human] = io.circe.generic.semiauto.deriveDecoder[Data.Character.Human]
      }
      case class Droid(override val id: String, override val name: Option[String] = None, override val friends: Option[List[Data.Character.Friends]] = None, val primaryFunction: Option[String] = None) extends Character()
      object Droid {
        val id: monocle.Lens[Data.Character.Droid, String] = monocle.macros.GenLens[Data.Character.Droid](_.id)
        val name: monocle.Lens[Data.Character.Droid, Option[String]] = monocle.macros.GenLens[Data.Character.Droid](_.name)
        val friends: monocle.Lens[Data.Character.Droid, Option[List[Data.Character.Friends]]] = monocle.macros.GenLens[Data.Character.Droid](_.friends)
        val primaryFunction: monocle.Lens[Data.Character.Droid, Option[String]] = monocle.macros.GenLens[Data.Character.Droid](_.primaryFunction)
        implicit val eqDroid: cats.Eq[Data.Character.Droid] = cats.Eq.fromUniversalEquals
        implicit val showDroid: cats.Show[Data.Character.Droid] = cats.Show.fromToString
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
      implicit val jsonDecoderCharacter: io.circe.Decoder[Data.Character] = List[io.circe.Decoder[Data.Character]](io.circe.Decoder[Data.Character.Human].asInstanceOf[io.circe.Decoder[Data.Character]], io.circe.Decoder[Data.Character.Droid].asInstanceOf[io.circe.Decoder[Data.Character]]).reduceLeft(_ or _)
    }
    val character: monocle.Lens[Data, Option[Data.Character]] = monocle.macros.GenLens[Data](_.character)
    implicit val eqData: cats.Eq[Data] = cats.Eq.fromUniversalEquals
    implicit val showData: cats.Show[Data] = cats.Show.fromToString
    implicit val jsonDecoderData: io.circe.Decoder[Data] = io.circe.generic.semiauto.deriveDecoder[Data]
  }
  val varEncoder: io.circe.Encoder[Variables] = Variables.jsonEncoderVariables
  val dataDecoder: io.circe.Decoder[Data] = Data.jsonDecoderData
  def query[F[_]](charId: String)(implicit client: clue.TransactionalClient[F, StarWars]) = client.request(this)(Variables(charId))
}
// format: on
