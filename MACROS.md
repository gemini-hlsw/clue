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

interface Character {
  id: String!
  name: String
  friends: [Character!]
  appearsIn: [Episode!]
}

type Human implements Character {
  id: String!
  name: String
  friends: [Character!]
  appearsIn: [Episode!]
  homePlanet: String
}

type Droid implements Character {
  id: String!
  name: String
  friends: [Character!]
  appearsIn: [Episode!]
  primaryFunction: String
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
      implicit val jsonEncoderEpisode: io.circe.Encoder[Episode] = io.circe.Encoder.encodeString.contramap[Super]{
        case NewHope => "NEW_HOPE"
        case Empire => "EMPIRE"
        case Jedi => "JEDI"
      }
      implicit val jsonDecoderEpisode: io.circe.Decoder[Episode] = io.circe.Decoder.decodeString.emapTry(s => scala.util.Try(s match {
        case "NEW_HOPE" => NewHope
        case "EMPIRE"=> Empire
        case "JEDI"=> Jedi
      }))
    }
  }

  // This example doesn't have input types.
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
      |    ... on Human {
      |      homePlanet
      |    }      
      |    friends {
      |      id
      |      name
      |    }
      |    ... on Droid {
      |      primaryFunction
      |    }
      |    __typename      
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
      |    ... on Human {
      |      homePlanet
      |    }      
      |    friends {
      |      id
      |      name
      |    }
      |    ... on Droid {
      |      primaryFunction
      |    }
      |    __typename  
      |  }
      |}""".stripMargin

  // Operation parameters.
  case class Variables(episode: Episode)
  object Variables {
    // Lenses
    val episode: monocle.Lens[Variables, Episode] = monocle.macros.GenLens[Variables](_.episode)

    // Cats typeclasses
    implicit val eqVariables: cats.Eq[Variables] = cats.Eq.fromUniversalEquals
    implicit val showVariables: cats.Show[Variables] = cats.Show.fromToString

    // Circe typeclasses
    implicit val jsonEncoderVariables: io.circe.Encoder[Variables] = io.circe.generic.semiauto.deriveEncoder[Variables].mapJson(_.foldWith(clue.data.Input.dropIgnoreFolder))
  }

  // Operation result.
  case class Data(hero: Data.Hero)
  object Data {
    // Types are defined in a nested structure to avoid name clashes.

    // Sum type
    sealed trait Hero {
      val id: String
      val name: Option[String]
      val friends: Option[List[Data.Hero.Friends]]
    }

    object Hero {
      case class Friends(id: String, name: Option[String])
      object Friends {
        // Lenses
        val id: monocle.Lens[Data.Hero.Friends, String] = monocle.macros.GenLens[Data.Hero.Friends](_.id)
        val name: monocle.Lens[Data.Hero.Friends, Option[String]] = monocle.macros.GenLens[Data.Hero.Friends](_.name)

        // Cats typeclasses
        implicit val eqFriends: cats.Eq[Data.Hero.Friends] = cats.Eq.fromUniversalEquals
        implicit val showFriends: cats.Show[Data.Hero.Friends] = cats.Show.fromToString

        // Circe typeclasses
        implicit val jsonDecoderFriends: io.circe.Decoder[Data.Hero.Friends] = io.circe.generic.semiauto.deriveDecoder[Data.Hero.Friends]
      }

      case class Human(override val id: String, override val name: Option[String], homePlanet: Option[String], override val friends: Option[List[Data.Hero.Friends]]) extends Hero
      object Human {
        // Lenses
        val id: monocle.Lens[Data.Hero.Human, String] = monocle.macros.GenLens[Data.Hero.Human](_.id)
        val name: monocle.Lens[Data.Hero.Human, Option[String]] = monocle.macros.GenLens[Data.Hero.Human](_.name)
        val homePlanet: monocle.Lens[Data.Hero.Human, Option[String]] = monocle.macros.GenLens[Data.Hero.Human](_.homePlanet)
        val friends: monocle.Lens[Data.Hero.Human, Option[List[Data.Hero.Friends]]] = monocle.macros.GenLens[Data.Hero.Human](_.friends)

        // Cats typeclasses
        implicit val eqHuman: cats.Eq[Data.Hero.Human] = cats.Eq.fromUniversalEquals
        implicit val showHuman: cats.Show[Data.Hero.Human] = cats.Show.fromToString

        // Circe typeclasses
        implicit val jsonDecoderHuman: io.circe.Decoder[Data.Hero.Human] = io.circe.generic.semiauto.deriveDecoder[Data.Hero.Human]        
      }

      case class Droid(override val id: String, override val name: Option[String], override val friends: Option[List[Data.Hero.Friends]], primaryFunction: Option[String]) extends Hero
      object Droid {
        // Lenses
        val id: monocle.Lens[Data.Hero.Droid, String] = monocle.macros.GenLens[Data.Hero.Droid](_.id)
        val name: monocle.Lens[Data.Hero.Droid, Option[String]] = monocle.macros.GenLens[Data.Hero.Droid](_.name);
        val friends: monocle.Lens[Data.Hero.Droid, Option[List[Data.Hero.Friends]]] = monocle.macros.GenLens[Data.Hero.Droid](_.friends)
        val primaryFunction: monocle.Lens[Data.Hero.Droid, Option[String]] = monocle.macros.GenLens[Data.Hero.Droid](_.primaryFunction)

        // Cats typeclasses
        implicit val eqDroid: cats.Eq[Data.Hero.Droid] = cats.Eq.fromUniversalEquals
        implicit val showDroid: cats.Show[Data.Hero.Droid] = cats.Show.fromToString

        // Circe typeclasses
        implicit val jsonDecoderDroid: io.circe.Decoder[Data.Hero.Droid] = io.circe.generic.semiauto.deriveDecoder[Data.Hero.Droid]        
      }      

      // Lenses (for sum type)
      val id: monocle.Lens[Data.Hero, String] = monocle.Lens[Data.Hero, String](_.id)(v => _ match {
        case s: Data.Hero.Human => s.copy(id = v)
        case s: Data.Hero.Droid => s.copy(id = v)
      })
      val name: monocle.Lens[Data.Hero, Option[String]] = monocle.Lens[Data.Hero, Option[String]](_.name)(v => _ match {
        case s: Data.Hero.Human => s.copy(name = v)
        case s: Data.Hero.Droid => s.copy(name = v)
      })
      val friends: monocle.Lens[Data.Hero, Option[List[Data.Hero.Friends]]] = monocle.Lens[Data.Hero, Option[List[Data.Hero.Friends]]](_.friends)(v => _ match {
        case s: Data.Hero.Human => s.copy(friends = v)
        case s: Data.Hero.Droid => s.copy(friends = v)
      })       

      // Cats typeclasses
      implicit val eqHero: cats.Eq[Data.Hero] = cats.Eq.fromUniversalEquals
      implicit val showHero: cats.Show[Data.Hero] = cats.Show.fromToString

      // Circe typeclasses. For sum types requires circe-generic-extras.
      implicit private val jsonConfiguration: io.circe.generic.extras.Configuration = io.circe.generic.extras.Configuration.default.withDiscriminator("__typename");
      implicit val jsonDecoderHero: io.circe.Decoder[Data.Hero] = io.circe.generic.extras.semiauto.deriveConfiguredDecoder[Data.Hero]      
    }

    // Lenses
    val hero: monocle.Lens[Data, Data.Hero] = monocle.macros.GenLens[Data](_.hero)

    // Cats typeclasses
    implicit val eqData: cats.Eq[Data] = cats.Eq.fromUniversalEquals
    implicit val showData: cats.Show[Data] = cats.Show.fromToString

    // Circe typeclasses
    implicit val jsonDecoderData: io.circe.Decoder[Data] = io.circe.generic.semiauto.deriveDecoder[Data]
  }

  override val varEncoder: io.circe.Encoder[Variables] = Variables.jsonEncoderVariables
  override val dataDecoder: io.circe.Decoder[Data] = Data.jsonDecoderData

  // A convenience parametrized method is generated.
  def query[F[_]](episode: Episode)(implicit client: clue.TransactionalClient[F, StarWars]) = client.request(this)(Variables(episode))
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

