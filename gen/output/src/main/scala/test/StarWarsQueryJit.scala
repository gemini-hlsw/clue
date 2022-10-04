// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off

package test

import clue.GraphQLOperation
import test.StarWarsJit


object StarWarsQueryJit extends GraphQLOperation[StarWarsJit] {
  import StarWarsJit.Scalars._
  ignoreUnusedImportScalars()
  import StarWarsJit.Enums._
  ignoreUnusedImportEnums()
  import StarWarsJit.Types._
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
  extension (thiz: Data) @scala.annotation.targetName("Data_character") def character: Option[Data.Character] = _root_.io.circe.Decoder[Option[_root_.io.circe.Json]].decodeJson(thiz.asInstanceOf[_root_.io.circe.JsonObject].apply("character").get).asInstanceOf[Option[Data.Character]]
  object Data {
    sealed trait Character {
      val id: String
      val name: Option[String]
      val friends: Option[List[Data.Character.Friends]]
    }
    object Character {
      opaque type Friends = _root_.io.circe.Json
      extension (thiz: Friends) @scala.annotation.targetName("Friends_name") def name: Option[String] = _root_.io.circe.Decoder[Option[_root_.io.circe.Json]].decodeJson(thiz.asInstanceOf[_root_.io.circe.JsonObject].apply("name").get).asInstanceOf[Option[String]]
      object Friends {
        implicit val eqFriends: cats.Eq[Data.Character.Friends] = cats.Eq.fromUniversalEquals
        implicit val showFriends: cats.Show[Data.Character.Friends] = cats.Show.fromToString
        implicit val jsonDecoderFriends: io.circe.Decoder[Data.Character.Friends] = io.circe.generic.semiauto.deriveDecoder[Data.Character.Friends]
      }
      opaque type Human <: Character = Character
      extension (thiz: Human) @scala.annotation.targetName("Human_id") def id: String = _root_.io.circe.Decoder[_root_.io.circe.Json].decodeJson(thiz.asInstanceOf[_root_.io.circe.JsonObject].apply("id").get).asInstanceOf[String]
      extension (thiz: Human) @scala.annotation.targetName("Human_name") def name: Option[String] = _root_.io.circe.Decoder[Option[_root_.io.circe.Json]].decodeJson(thiz.asInstanceOf[_root_.io.circe.JsonObject].apply("name").get).asInstanceOf[Option[String]]
      extension (thiz: Human) @scala.annotation.targetName("Human_homePlanet") def homePlanet: Option[String] = _root_.io.circe.Decoder[Option[_root_.io.circe.Json]].decodeJson(thiz.asInstanceOf[_root_.io.circe.JsonObject].apply("homePlanet").get).asInstanceOf[Option[String]]
      extension (thiz: Human) @scala.annotation.targetName("Human_friends") def friends: Option[List[Data.Character.Friends]] = _root_.io.circe.Decoder[Option[List[_root_.io.circe.Json]]].decodeJson(thiz.asInstanceOf[_root_.io.circe.JsonObject].apply("friends").get).asInstanceOf[Option[List[Data.Character.Friends]]]
      object Human {
        implicit val eqHuman: cats.Eq[Data.Character.Human] = cats.Eq.fromUniversalEquals
        implicit val showHuman: cats.Show[Data.Character.Human] = cats.Show.fromToString
        implicit val jsonDecoderHuman: io.circe.Decoder[Data.Character.Human] = io.circe.generic.semiauto.deriveDecoder[Data.Character.Human]
      }
      opaque type Droid <: Character = Character
      extension (thiz: Droid) @scala.annotation.targetName("Droid_id") def id: String = _root_.io.circe.Decoder[_root_.io.circe.Json].decodeJson(thiz.asInstanceOf[_root_.io.circe.JsonObject].apply("id").get).asInstanceOf[String]
      extension (thiz: Droid) @scala.annotation.targetName("Droid_name") def name: Option[String] = _root_.io.circe.Decoder[Option[_root_.io.circe.Json]].decodeJson(thiz.asInstanceOf[_root_.io.circe.JsonObject].apply("name").get).asInstanceOf[Option[String]]
      extension (thiz: Droid) @scala.annotation.targetName("Droid_friends") def friends: Option[List[Data.Character.Friends]] = _root_.io.circe.Decoder[Option[List[_root_.io.circe.Json]]].decodeJson(thiz.asInstanceOf[_root_.io.circe.JsonObject].apply("friends").get).asInstanceOf[Option[List[Data.Character.Friends]]]
      extension (thiz: Droid) @scala.annotation.targetName("Droid_primaryFunction") def primaryFunction: Option[String] = _root_.io.circe.Decoder[Option[_root_.io.circe.Json]].decodeJson(thiz.asInstanceOf[_root_.io.circe.JsonObject].apply("primaryFunction").get).asInstanceOf[Option[String]]
      object Droid {
        implicit val eqDroid: cats.Eq[Data.Character.Droid] = cats.Eq.fromUniversalEquals
        implicit val showDroid: cats.Show[Data.Character.Droid] = cats.Show.fromToString
        implicit val jsonDecoderDroid: io.circe.Decoder[Data.Character.Droid] = io.circe.generic.semiauto.deriveDecoder[Data.Character.Droid]
      }
      implicit val eqCharacter: cats.Eq[Data.Character] = cats.Eq.fromUniversalEquals
      implicit val showCharacter: cats.Show[Data.Character] = cats.Show.fromToString
      implicit val jsonDecoderCharacter: io.circe.Decoder[Data.Character] = List[io.circe.Decoder[Data.Character]](io.circe.Decoder[Data.Character.Human].asInstanceOf[io.circe.Decoder[Data.Character]], io.circe.Decoder[Data.Character.Droid].asInstanceOf[io.circe.Decoder[Data.Character]]).reduceLeft(_ or _)
    }
    implicit val eqData: cats.Eq[Data] = cats.Eq.fromUniversalEquals
    implicit val showData: cats.Show[Data] = cats.Show.fromToString
    implicit val jsonDecoderData: io.circe.Decoder[Data] = io.circe.generic.semiauto.deriveDecoder[Data]
  }
  val varEncoder: io.circe.Encoder[Variables] = Variables.jsonEncoderVariables
  val dataDecoder: io.circe.Decoder[Data] = Data.jsonDecoderData
  def query[F[_]](charId: String)(implicit client: clue.TransactionalClient[F, StarWarsJit]) = client.request(this)(Variables(charId))
}
// format: on
