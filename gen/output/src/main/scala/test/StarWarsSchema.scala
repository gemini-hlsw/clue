// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off

package test



sealed trait StarWars
object StarWars {
  object Scalars { def ignoreUnusedImportScalars(): Unit = () }
  object Enums {
    def ignoreUnusedImportEnums(): Unit = ()
    sealed abstract class Episode(val asString: String)
    object Episode {
      case object Newhope extends Episode("NEWHOPE")
      case object Empire extends Episode("EMPIRE")
      case object Jedi extends Episode("JEDI")
      val values: List[Episode] = List(Newhope, Empire, Jedi)
      def fromString(s: String): Either[String, Episode] = s match {
        case "NEWHOPE" =>
          Right(Newhope)
        case "EMPIRE" =>
          Right(Empire)
        case "JEDI" =>
          Right(Jedi)
        case _ =>
          Left(s"Invalid value [$s] for enum [" + "Episode" + "]")
      }
      implicit val eqEpisode: cats.Eq[Episode] = cats.Eq.fromUniversalEquals
      implicit val showEpisode: cats.Show[Episode] = cats.Show.fromToString
      implicit val jsonEncoderEpisode: io.circe.Encoder[Episode] = io.circe.Encoder.encodeString.contramap[Episode](_.asString)
      implicit val jsonDecoderEpisode: io.circe.Decoder[Episode] = io.circe.Decoder.decodeString.emap(fromString(_))
    }
  }
  object Types {
    import Scalars._
    ignoreUnusedImportScalars()
    import Enums._
    ignoreUnusedImportEnums()
    def ignoreUnusedImportTypes(): Unit = ()
  }
}
// format: on
