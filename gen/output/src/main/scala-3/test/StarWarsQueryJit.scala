// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off

package test

import clue.GraphQLOperation
import test.StarWars


object StarWarsQueryJit extends GraphQLOperation[StarWars] {
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
    implicit val eqVariables: cats.Eq[Variables] = cats.Eq.fromUniversalEquals
    implicit val showVariables: cats.Show[Variables] = cats.Show.fromToString
    implicit val jsonEncoderVariables: io.circe.Encoder[Variables] = io.circe.generic.semiauto.deriveEncoder[Variables].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
  }
  opaque type Data = _root_.io.circe.Json
  extension (thiz: Data) @scala.annotation.targetName("Data_character") def character: Option[Data.Character] = _root_.io.circe.Decoder[Option[Data.Character]].decodeJson(thiz.asInstanceOf[_root_.io.circe.JsonObject].apply("character").get).toTry.get
  object Data {
    sealed trait Character {
      val id: String
      val name: Option[String]
      val friends: Option[List[Data.Character.Friends]]
    }
    object Character {
      opaque type Friends = _root_.io.circe.Json
      extension (thiz: Friends) @scala.annotation.targetName("Friends_name") def name: Option[String] = _root_.io.circe.Decoder[Option[String]].decodeJson(thiz.asInstanceOf[_root_.io.circe.JsonObject].apply("name").get).toTry.get
      object Friends {
        implicit val eqFriends: cats.Eq[Data.Character.Friends] = _root_.io.circe.Json.eqJson.asInstanceOf[cats.Eq[Data.Character.Friends]]
        implicit val showFriends: cats.Show[Data.Character.Friends] = _root_.io.circe.Json.showJson.asInstanceOf[cats.Show[Data.Character.Friends]]
        implicit val jsonDecoderFriends: io.circe.Decoder[Data.Character.Friends] = io.circe.Decoder.decodeJson.asInstanceOf[io.circe.Decoder[Data.Character.Friends]]
      }
      opaque type Human <: Character = Character
      extension (thiz: Human) @scala.annotation.targetName("Human_id") def id: String = _root_.io.circe.Decoder[String].decodeJson(thiz.asInstanceOf[_root_.io.circe.JsonObject].apply("id").get).toTry.get
      extension (thiz: Human) @scala.annotation.targetName("Human_name") def name: Option[String] = _root_.io.circe.Decoder[Option[String]].decodeJson(thiz.asInstanceOf[_root_.io.circe.JsonObject].apply("name").get).toTry.get
      extension (thiz: Human) @scala.annotation.targetName("Human_homePlanet") def homePlanet: Option[String] = _root_.io.circe.Decoder[Option[String]].decodeJson(thiz.asInstanceOf[_root_.io.circe.JsonObject].apply("homePlanet").get).toTry.get
      extension (thiz: Human) @scala.annotation.targetName("Human_friends") def friends: Option[List[Data.Character.Friends]] = _root_.io.circe.Decoder[Option[List[Data.Character.Friends]]].decodeJson(thiz.asInstanceOf[_root_.io.circe.JsonObject].apply("friends").get).toTry.get
      object Human {
        implicit val eqHuman: cats.Eq[Data.Character.Human] = _root_.io.circe.Json.eqJson.asInstanceOf[cats.Eq[Data.Character.Human]]
        implicit val showHuman: cats.Show[Data.Character.Human] = _root_.io.circe.Json.showJson.asInstanceOf[cats.Show[Data.Character.Human]]
        implicit val jsonDecoderHuman: io.circe.Decoder[Data.Character.Human] = io.circe.Decoder.decodeJson.asInstanceOf[io.circe.Decoder[Data.Character.Human]]
      }
      opaque type Droid <: Character = Character
      extension (thiz: Droid) @scala.annotation.targetName("Droid_id") def id: String = _root_.io.circe.Decoder[String].decodeJson(thiz.asInstanceOf[_root_.io.circe.JsonObject].apply("id").get).toTry.get
      extension (thiz: Droid) @scala.annotation.targetName("Droid_name") def name: Option[String] = _root_.io.circe.Decoder[Option[String]].decodeJson(thiz.asInstanceOf[_root_.io.circe.JsonObject].apply("name").get).toTry.get
      extension (thiz: Droid) @scala.annotation.targetName("Droid_friends") def friends: Option[List[Data.Character.Friends]] = _root_.io.circe.Decoder[Option[List[Data.Character.Friends]]].decodeJson(thiz.asInstanceOf[_root_.io.circe.JsonObject].apply("friends").get).toTry.get
      extension (thiz: Droid) @scala.annotation.targetName("Droid_primaryFunction") def primaryFunction: Option[String] = _root_.io.circe.Decoder[Option[String]].decodeJson(thiz.asInstanceOf[_root_.io.circe.JsonObject].apply("primaryFunction").get).toTry.get
      object Droid {
        implicit val eqDroid: cats.Eq[Data.Character.Droid] = _root_.io.circe.Json.eqJson.asInstanceOf[cats.Eq[Data.Character.Droid]]
        implicit val showDroid: cats.Show[Data.Character.Droid] = _root_.io.circe.Json.showJson.asInstanceOf[cats.Show[Data.Character.Droid]]
        implicit val jsonDecoderDroid: io.circe.Decoder[Data.Character.Droid] = io.circe.Decoder.decodeJson.asInstanceOf[io.circe.Decoder[Data.Character.Droid]]
      }
      implicit val eqCharacter: cats.Eq[Data.Character] = _root_.io.circe.Json.eqJson.asInstanceOf[cats.Eq[Data.Character]]
      implicit val showCharacter: cats.Show[Data.Character] = _root_.io.circe.Json.showJson.asInstanceOf[cats.Show[Data.Character]]
      implicit val jsonDecoderCharacter: io.circe.Decoder[Data.Character] = io.circe.Decoder.decodeJson.asInstanceOf[io.circe.Decoder[Data.Character]]
    }
    implicit val eqData: cats.Eq[Data] = _root_.io.circe.Json.eqJson.asInstanceOf[cats.Eq[Data]]
    implicit val showData: cats.Show[Data] = _root_.io.circe.Json.showJson.asInstanceOf[cats.Show[Data]]
    implicit val jsonDecoderData: io.circe.Decoder[Data] = io.circe.Decoder.decodeJson.asInstanceOf[io.circe.Decoder[Data]]
  }
  val varEncoder: io.circe.Encoder[Variables] = Variables.jsonEncoderVariables
  val dataDecoder: io.circe.Decoder[Data] = Data.jsonDecoderData
  def query[F[_]](charId: String)(implicit client: clue.TransactionalClient[F, StarWars]) = client.request(this)(Variables(charId))
}
// format: on
