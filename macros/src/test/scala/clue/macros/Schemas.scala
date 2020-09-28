package clue.macros

object Schemas {
  @GraphQLSchema(debug = false)
  object Explore {
    object Scalars {
      type Cloudcover       = String
      type Imagequality     = String
      type Skybackground    = String
      type Watervapor       = String
      type Obsstatus        = String
      type Targetobjecttype = String
    }

    object Types {}
  }

  @GraphQLSchema(debug = false)
  object StarWars
}

import Schemas._
import clue._

object BasicQuery extends GraphQLOperation[StarWars] {
  import StarWars.Types._;
  ignoreUnusedImportTypes();
  val document = ""
  case class Variables() extends scala.Product with scala.Serializable {};
  object Variables       extends scala.AnyRef                          {
    implicit val eqVariables: cats.Eq[Variables]                   = cats.Eq.fromUniversalEquals;
    implicit val showVariables: cats.Show[Variables]               = cats.Show.fromToString;
    implicit val jsonEncoderVariables: io.circe.Encoder[Variables] =
      io.circe.generic.semiauto.deriveEncoder[Variables].mapJson((x$4 => x$4.deepDropNullValues))
  };
  case class Data(character: Option[Data.Character])
  object Data            extends scala.AnyRef                          {
    case class Character(friends: Option[List[Data.Character.Friends]] = None)
    object Character extends scala.AnyRef {
      case class Friends(friends: Option[List[Data.Character.Friends.Friends]] = None)
      object Friends extends scala.AnyRef {
        case class Friends(name: Option[String] = None)
        object Friends extends scala.AnyRef {
          implicit val eqFriends: cats.Eq[Data.Character.Friends.Friends]                   =
            cats.Eq.fromUniversalEquals;
          implicit val showFriends: cats.Show[Data.Character.Friends.Friends]               =
            cats.Show.fromToString;
          implicit val jsonDecoderFriends: io.circe.Decoder[Data.Character.Friends.Friends] =
            io.circe.generic.semiauto.deriveDecoder[Data.Character.Friends.Friends]
        };
        implicit val eqFriends: cats.Eq[Data.Character.Friends] = cats.Eq.fromUniversalEquals;
        implicit val showFriends: cats.Show[Data.Character.Friends]               = cats.Show.fromToString;
        implicit val jsonDecoderFriends: io.circe.Decoder[Data.Character.Friends] =
          io.circe.generic.semiauto.deriveDecoder[Data.Character.Friends]
      };
      implicit val eqCharacter: cats.Eq[Data.Character] = cats.Eq.fromUniversalEquals;
      implicit val showCharacter: cats.Show[Data.Character]               = cats.Show.fromToString;
      implicit val jsonDecoderCharacter: io.circe.Decoder[Data.Character] =
        io.circe.generic.semiauto.deriveDecoder[Data.Character]
    };
    implicit val eqData: cats.Eq[Data] = cats.Eq.fromUniversalEquals;
    implicit val showData: cats.Show[Data]               = cats.Show.fromToString;
    implicit val jsonDecoderData: io.circe.Decoder[Data] =
      io.circe.generic.semiauto.deriveDecoder[Data]
  };
  val varEncoder: io.circe.Encoder[Variables] = Variables.jsonEncoderVariables;
  val dataDecoder: io.circe.Decoder[Data] = Data.jsonDecoderData;
  def query[F[_]]()(implicit client: _root_.clue.GraphQLClient[F, StarWars]) =
    client.request(this)(Variables())
}
