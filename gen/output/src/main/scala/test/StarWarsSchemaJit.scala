// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// format: off

package test



sealed trait StarWarsJit
object StarWarsJit {
  object Scalars { def ignoreUnusedImportScalars(): Unit = () }
  object Enums {
    def ignoreUnusedImportEnums(): Unit = ()
    opaque type Episode = String
    object Episode {
      opaque type Newhope <: Episode = String
      opaque type Empire <: Episode = String
      opaque type Jedi <: Episode = String
      implicit val eqEpisode: cats.Eq[Episode] = cats.Eq.fromUniversalEquals
      implicit val showEpisode: cats.Show[Episode] = cats.Show.fromToString
      implicit val jsonEncoderEpisode: io.circe.Encoder[Episode] = io.circe.Encoder.encodeString.contramap[Episode]({
        case Newhope => "NEWHOPE"
        case Empire => "EMPIRE"
        case Jedi => "JEDI"
      })
      implicit val jsonDecoderEpisode: io.circe.Decoder[Episode] = io.circe.Decoder.decodeString.emap(_ match {
        case "NEWHOPE" =>
          Right(Newhope)
        case "EMPIRE" =>
          Right(Empire)
        case "JEDI" =>
          Right(Jedi)
        case other =>
          Left(s"Invalid value [$other]")
      })
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
