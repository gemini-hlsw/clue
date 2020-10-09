# Macros

## Example

Given the following schema, placed in `StarWars.graphql`:

``` graphql
type Query {
  hero(episode: Episode!): Character!
}

enum Episode {
  NEW_HOPE
  EMPIRE
  JEDI
}

scalar Height

type Character {
  id: String!
  name: String
  height: Height
  friends: [Character!]
  appearsIn: [Episode!]
}
```

Defining:
``` scala
import clue.macros._

@GraphQLSchema
object StarWars {
  // Scalars must be provided and have Encoder/Decoder instances, as required.
  object Scalars {
    type Height = Int
  }
}
```

Will generate:
``` scala
// For easy typing of operations and clients.
sealed abstract trait StarWars
object StarWars {

  object Scalars {
    type Height = Int
  }
  
  object Enums {
    sealed trait Episode
    object Episode {
      case object NewHope extends Episode
      case object Empire extends Episode
      case object Jedi extends Episode

      implicit val eqEpisode: cats.Eq[Episode] = cats.Eq.fromUniversalEquals
      implicit val showEpisode: cats.Show[Episode] = cats.Show.fromToString
      implicit val jsonEncoderEpisode: io.circe.Encoder[Episode] = io.circe.generic.semiauto.deriveEncoder[Episode]
      implicit val jsonDecoderEpisode: io.circe.Decoder[Episode] = io.circe.generic.semiauto.deriveDecoder[Episode]
    }
  }

  // This example doesnt have input types.
  object Types {}
}
```

And defining:

``` scala
@GraphQL
object BasicQuery extends GraphQLOperation[StarWars] {
  val document = """|
      |query ($episode: Episode!) {
      |  hero(episode: $episode) {
      |    id
      |    name
      |    friends {
      |      id
      |      name
      |    }
      |  }
      |}""".stripMargin
}
```

Will generate:
``` scala
object BasicQuery extends GraphQLOperation[StarWars] {
  import StarWars.Enums._

  // Not modified.
  val document = """|
      |query ($episode: Episode!) {
      |  hero(episode: $episode) {
      |    id
      |    name
      |    friends {
      |      id
      |      name
      |    }
      |  }
      |}""".stripMargin

  // Operation parameters.
  case class Variables(episode: Episode)
  object Variables {
    val episode: monocle.Lens[Variables, Episode] = monocle.macros.GenLens[Variables](_.episode)

    implicit val eqVariables: cats.Eq[Variables] = cats.Eq.fromUniversalEquals
    implicit val showVariables: cats.Show[Variables] = cats.Show.fromToString
    implicit val jsonEncoderVariables: io.circe.Encoder[Variables] = io.circe.generic.semiauto.deriveEncoder[Variables]
  }

  // Operation result.
  case class Data(hero: Data.Hero)
  object Data {
    // Classes are defined in a nested structure to avoid name clashes.
    case class Hero(id: String, name: Option[String], friends: Option[List[Data.Hero.Friends]])
    object Hero {
      case class Friends(id: String, name: Option[String])
      object Friends {
        val id: monocle.Lens[Data.Hero.Friends, String] = monocle.macros.GenLens[Data.Hero.Friends](_.id)
        val name: monocle.Lens[Data.Hero.Friends, Option[String]] = monocle.macros.GenLens[Data.Hero.Friends](_.name)

        implicit val eqFriends: cats.Eq[Data.Hero.Friends] = cats.Eq.fromUniversalEquals
        implicit val showFriends: cats.Show[Data.Hero.Friends] = cats.Show.fromToString
        implicit val jsonDecoderFriends: io.circe.Decoder[Data.Hero.Friends] = io.circe.generic.semiauto.deriveDecoder[Data.Hero.Friends]
      }

      val id: monocle.Lens[Data.Hero, String] = monocle.macros.GenLens[Data.Hero](_.id)
      val name: monocle.Lens[Data.Hero, Option[String]] = monocle.macros.GenLens[Data.Hero](_.name)
      val friends: monocle.Lens[Data.Hero, Option[List[Data.Hero.Friends]]] = monocle.macros.GenLens[Data.Hero](_.friends)

      implicit val eqHero: cats.Eq[Data.Hero] = cats.Eq.fromUniversalEquals
      implicit val showHero: cats.Show[Data.Hero] = cats.Show.fromToString
      implicit val jsonDecoderHero: io.circe.Decoder[Data.Hero] = io.circe.generic.semiauto.deriveDecoder[Data.Hero]
    }

    val hero: monocle.Lens[Data, Data.Hero] = monocle.macros.GenLens[Data](_.hero)
    implicit val eqData: cats.Eq[Data] = cats.Eq.fromUniversalEquals
    implicit val showData: cats.Show[Data] = cats.Show.fromToString
    implicit val jsonDecoderData: io.circe.Decoder[Data] = io.circe.generic.semiauto.deriveDecoder[Data]
  }

  override val varEncoder: io.circe.Encoder[Variables] = Variables.jsonEncoderVariables
  override val dataDecoder: io.circe.Decoder[Data] = Data.jsonDecoderData

  // A convenience parametrized method is generated.
  def query[F[_]](episode: Episode)(implicit client: clue.GraphQLClient[F, StarWars]) = client.request(this)(Variables(episode))
}
```

## Customization

The macro expander can be customized in two ways:

### Via compiler options

You can pass options via `-Xmacro-settings:key=value[,key=value...]` parameters passed to the compiler (you can pass multiple such parameters, separate options with commas, or a combination of both).

Possible options are:
* `clue.schemaDir=<absolute path>` - Tells the macro where to look for schema `.graphql` files. Multiple directories can be passed by specifying this option multiple times.
* `clue.cats.eq=true|false` - Whether to generate `cats.Eq` instances for generated classes. They will just be `Eq.fromUniversalEquals`. `true` by default.
* `clue.cats.show=true|false` - Whether to generate `cats.Show` instances for generated classes. They will just be `Show.fromToString`. `true` by default.
* `clue.monocle.lenses=true|false` - Whether to generate `monocle.Lens` vals for class members. `true` by default.
* `clue.scalajs-react.reusability=true|false` - Whether to generate `japgolly.scalajs.react.Reusability` instances for generated classes. They will be `Reusability.derive`. `false` by default.

### Via annotation parameters

Both annotations support the following parameters, which will override compiler option settings.

* `eq: Boolean`
* `show: Boolean`
* `lenses: Boolean`
* `reuse: Boolean`

Additionally, the following parameter can be used in both annotations to dump the generated code to the console or to the IDE (if it supports it):

* `debug: Boolean`

