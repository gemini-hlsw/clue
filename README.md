# clue

Experimental GraphQL client.

## Usage

### 1) Create a client

Either:
  * A `TransactionalClient[F[_], S]` (supporting queries and mutations), or
  * A `StreamingClient[F[_], S]` (supporting queries, mutations and subscriptions).

  `S` is a type denoting the schema. It can be any type, even a phantom type. It's only used to type-match clients and operations.

#### Example

``` scala
  import clue._
  import clue.js._
  import cats.effect.IO
  import org.typelevel.log4cats.Logger

  sealed trait StarWars

  implicit val backend: Backend[IO] = AjaxJSBackend[IO]
  val transactionalClient: TransactionalClient[IO, StarWars] = 
    TransactionalClient.of[IO, Schema]("https://starwars.com/graphql")

  implicit val streamingBackend: StreamingBackend[IO] = WebSocketJSBackend[IO]
  val streamingClient: StreamingClient[IO, StarWars] = 
    ApolloStreamingClient.of[IO, Schema]("wss://starwars.com/graphql")
```

NOTES: 
* `ApolloStreamingClient` implements the "de facto" Apollo protocol for streaming over web sockets.
* The `TransactionalClient.of` constructor requires an implicit instance of a `Backend[F]`. An `AjaxJSBackend[F]` is provided for Scala.js.
* The `ApolloStreamingClient.of` constructor requires an implicit instance of a `StreamingBackend[F]`. A `WebSocketJSBackend[F]` is provided for Scala.js.


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
transactionalClient.request(CharacterQuery)(CharacterQuery.Variables("0001"))
  .forEach(println).unsafeRunSync()

# Data(Some(Character("0001", Some("Luke"))))
```