# clue

Experimental GraphQL client for Scala and Scala.js.

## Usage

### 1) Create a client

Either:
  * A `FetchClient[F[_], S]` (supporting queries and mutations), or
  * A `StreamingClient[F[_], S]` (supporting queries, mutations and subscriptions).

  `S` is a type denoting the schema. It can be any type, even a phantom type. It's only used to type-match clients and operations.

#### Example

``` scala
  import clue.*
  import cats.effect.IO

  sealed trait StarWars

  // Scala JVM and Scala.js with http4s Ember client
  import org.http4s.ember.client.EmberClientBuilder

  EmberClientBuilder
    .default[IO]
    .build
    .use: client => 
      given Backend[IO] = Http4sHttpBackend[IO](client)
      val fetchClient: FetchClient[IO, StarWars] = 
        Http4sHttpClient.of[IO, StarWars]("https://starwars.com/graphql")
    
  // Scala JVM with JDK WS client behind http4s
  import import org.http4s.jdkhttpclient.JdkWSClient

  JdkWSClient
    .simple[IO]
    .use: client =>
      given StreamingBackend[IO] = Http4sWebSocketBackend[IO](client)
      val streamingClient: StreamingClient[IO, StarWars] = 
        Http4sWebSocketClient.of[IO, StarWars]("wss://starwars.com/graphql")


  // Scala.js with default fetch/WS client
  import clue.js.*

  given Backend[IO] = AjaxJSBackend[IO]
  val fetchClient: FetchClient[IO, StarWars] = 
    FetchJsClient.of[IO, StarWars]("https://starwars.com/graphql")

  // Streaming doesn't require Apollo, it just follows the Apollo protocol for GraphQL over WS
  given StreamingBackend[IO] = WebSocketJsBackend[IO]
  val streamingClient: StreamingClient[IO, StarWars] = 
    ApolloStreamingClient.of[IO, StarWars]("wss://starwars.com/graphql")
```

### 2) Create operations

They must extend `GraphQLOperation[S]`, defining the following members:

``` scala
  val document: String

  type Variables
  type Data

  val varEncoder: io.circe.Encoder.AsObject[Variables]
  val dataDecoder: io.circe.Decoder[Data]
```

#### Example

``` scala
  import io.circe._
  import io.circe.generic.semiauto._

  object CharacterQuery extends GraphQLOperation[StarWars] {
    val document = """
        query (charId: ID!) {
          character(id: $charId) {
            id
            name
          }
        }
      """

    case class Variables(charId: String)

    case class Character(id: String, name: Option[String])
    object Character {
      implicit val characterDecoder: Decoder[Character] = deriveDecoder[Character]
    }

    case class Data(character: Option[Character])

    val varEncoder: Encoder[Variables] = deriveEncoder[Variables]
    val dataDecoder: Decoder[Data] = deriveDecoder[Data]
  }
```

### 3) Invoke operations

#### Example

``` scala
fetchClient.request(CharacterQuery)(CharacterQuery.Variables("0001"))
  .forEach(println).unsafeRunSync()

# Data(Some(Character("0001", Some("Luke"))))
```