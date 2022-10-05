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
  extension (thiz: Data) @scala.annotation.targetName("Data_character") def character: Option[Data.Character] = thiz.characterAttempt.toTry.get
  extension (thiz: Data) @scala.annotation.targetName("Data_characterAttempt") def characterAttempt: Either[Throwable, Option[Data.Character]] = _root_.io.circe.Decoder[Option[Data.Character]].decodeJson(thiz.asInstanceOf[_root_.io.circe.JsonObject].apply("character").get)
  object Data {
    def unapply(thiz: Data): Option[Option[Data.Character]] = thiz.characterAttempt.toOption
    sealed trait Character {
      val id: String
      val name: Option[String]
      val friends: Option[List[Data.Character.Friends]]
    }
    object Character {
      opaque type Friends = _root_.io.circe.Json
      extension (thiz: Friends) @scala.annotation.targetName("Friends_name") def name: Option[String] = thiz.nameAttempt.toTry.get
      extension (thiz: Friends) @scala.annotation.targetName("Friends_nameAttempt") def nameAttempt: Either[Throwable, Option[String]] = _root_.io.circe.Decoder[Option[String]].decodeJson(thiz.asInstanceOf[_root_.io.circe.JsonObject].apply("name").get)
      object Friends {
        def unapply(thiz: Friends): Option[Option[String]] = thiz.nameAttempt.toOption
        implicit val eqFriends: cats.Eq[Data.Character.Friends] = _root_.io.circe.Json.eqJson.asInstanceOf[cats.Eq[Data.Character.Friends]]
        implicit val showFriends: cats.Show[Data.Character.Friends] = _root_.io.circe.Json.showJson.asInstanceOf[cats.Show[Data.Character.Friends]]
        implicit val jsonDecoderFriends: io.circe.Decoder[Data.Character.Friends] = io.circe.Decoder.decodeJson.asInstanceOf[io.circe.Decoder[Data.Character.Friends]]
      }
      opaque type Human <: Character = Character
      extension (thiz: Human) @scala.annotation.targetName("Human_id") def id: String = thiz.idAttempt.toTry.get
      extension (thiz: Human) @scala.annotation.targetName("Human_idAttempt") def idAttempt: Either[Throwable, String] = _root_.io.circe.Decoder[String].decodeJson(thiz.asInstanceOf[_root_.io.circe.JsonObject].apply("id").get)
      extension (thiz: Human) @scala.annotation.targetName("Human_name") def name: Option[String] = thiz.nameAttempt.toTry.get
      extension (thiz: Human) @scala.annotation.targetName("Human_nameAttempt") def nameAttempt: Either[Throwable, Option[String]] = _root_.io.circe.Decoder[Option[String]].decodeJson(thiz.asInstanceOf[_root_.io.circe.JsonObject].apply("name").get)
      extension (thiz: Human) @scala.annotation.targetName("Human_homePlanet") def homePlanet: Option[String] = thiz.homePlanetAttempt.toTry.get
      extension (thiz: Human) @scala.annotation.targetName("Human_homePlanetAttempt") def homePlanetAttempt: Either[Throwable, Option[String]] = _root_.io.circe.Decoder[Option[String]].decodeJson(thiz.asInstanceOf[_root_.io.circe.JsonObject].apply("homePlanet").get)
      extension (thiz: Human) @scala.annotation.targetName("Human_friends") def friends: Option[List[Data.Character.Friends]] = thiz.friendsAttempt.toTry.get
      extension (thiz: Human) @scala.annotation.targetName("Human_friendsAttempt") def friendsAttempt: Either[Throwable, Option[List[Data.Character.Friends]]] = _root_.io.circe.Decoder[Option[List[Data.Character.Friends]]].decodeJson(thiz.asInstanceOf[_root_.io.circe.JsonObject].apply("friends").get)
      object Human {
        def unapply(thiz: Human): Option[(String, Option[String], Option[String], Option[List[Data.Character.Friends]])] = cats.Semigroupal.tuple4(thiz.idAttempt, thiz.nameAttempt, thiz.homePlanetAttempt, thiz.friendsAttempt).toOption
        implicit val eqHuman: cats.Eq[Data.Character.Human] = _root_.io.circe.Json.eqJson.asInstanceOf[cats.Eq[Data.Character.Human]]
        implicit val showHuman: cats.Show[Data.Character.Human] = _root_.io.circe.Json.showJson.asInstanceOf[cats.Show[Data.Character.Human]]
        implicit val jsonDecoderHuman: io.circe.Decoder[Data.Character.Human] = io.circe.Decoder.decodeJson.asInstanceOf[io.circe.Decoder[Data.Character.Human]]
      }
      opaque type Droid <: Character = Character
      extension (thiz: Droid) @scala.annotation.targetName("Droid_id") def id: String = thiz.idAttempt.toTry.get
      extension (thiz: Droid) @scala.annotation.targetName("Droid_idAttempt") def idAttempt: Either[Throwable, String] = _root_.io.circe.Decoder[String].decodeJson(thiz.asInstanceOf[_root_.io.circe.JsonObject].apply("id").get)
      extension (thiz: Droid) @scala.annotation.targetName("Droid_name") def name: Option[String] = thiz.nameAttempt.toTry.get
      extension (thiz: Droid) @scala.annotation.targetName("Droid_nameAttempt") def nameAttempt: Either[Throwable, Option[String]] = _root_.io.circe.Decoder[Option[String]].decodeJson(thiz.asInstanceOf[_root_.io.circe.JsonObject].apply("name").get)
      extension (thiz: Droid) @scala.annotation.targetName("Droid_friends") def friends: Option[List[Data.Character.Friends]] = thiz.friendsAttempt.toTry.get
      extension (thiz: Droid) @scala.annotation.targetName("Droid_friendsAttempt") def friendsAttempt: Either[Throwable, Option[List[Data.Character.Friends]]] = _root_.io.circe.Decoder[Option[List[Data.Character.Friends]]].decodeJson(thiz.asInstanceOf[_root_.io.circe.JsonObject].apply("friends").get)
      extension (thiz: Droid) @scala.annotation.targetName("Droid_primaryFunction") def primaryFunction: Option[String] = thiz.primaryFunctionAttempt.toTry.get
      extension (thiz: Droid) @scala.annotation.targetName("Droid_primaryFunctionAttempt") def primaryFunctionAttempt: Either[Throwable, Option[String]] = _root_.io.circe.Decoder[Option[String]].decodeJson(thiz.asInstanceOf[_root_.io.circe.JsonObject].apply("primaryFunction").get)
      object Droid {
        def unapply(thiz: Droid): Option[(String, Option[String], Option[List[Data.Character.Friends]], Option[String])] = cats.Semigroupal.tuple4(thiz.idAttempt, thiz.nameAttempt, thiz.friendsAttempt, thiz.primaryFunctionAttempt).toOption
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
