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
  val document: GraphQLDocument   // built with the `gql` interpolator (see below)

  type Variables
  type Data

  val varEncoder: io.circe.Encoder.AsObject[Variables]
  val dataDecoder: io.circe.Decoder[Data]
```

The `document` is built with the `gql` string interpolator (`import clue.gql`) rather than a plain
`String`/`s"..."`. `gql` produces the same text at runtime, but its type (`GraphQLDocument`) can only
be obtained through `gql`, so every operation goes through the compile-time check that splices its
subqueries correctly (see [Subquery variables](#subquery-variables) below). For a document built by
other means, `GraphQLDocument.unsafeFromString(...)` is the explicit (check-skipping) escape hatch.

#### Example

``` scala
  import io.circe._
  import io.circe.generic.semiauto._

  object CharacterQuery extends GraphQLOperation[StarWars] {
    val document = gql"""
        query ($$charId: ID!) {
          character(id: $$charId) {
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

#### Validating operations against the schema

Hand-written operations and subqueries (those defined manually, without the code generator) are
validated against the schema by the `GraphQLValidate` rule, which reuses the same checks as the
generator — no code generation involved. Any definition extending `GraphQLOperation[S]` /
`GraphQLOperation.Typed[S, ...]` (checked via its `document`) or `GraphQLSubquery[S]` /
`GraphQLSubquery.Typed[S, ...]` (checked via its `subquery`) is validated; fields, arguments,
variables and deprecations that don't typecheck against schema `S` are reported as scalafix
diagnostics. Operations that *are* generated (annotated with `@GraphQL`) are validated during
generation.

A subquery declares the GraphQL root type(s) its selection applies to with a
`@clue.annotation.GraphQLType` annotation:

```scala
@GraphQLType("Character")
abstract class FriendFields extends GraphQLSubquery.Typed[StarWars, Json] {
  val subquery = gql"{ id name }"
}
```

A subquery's `subquery` is a `GraphQLDocument` too, so it is built with `gql` for the same reason a
`document` is (see [Subquery variables](#subquery-variables)).

Hand-written subqueries may declare **multiple** types (the selection is validated against each:
`@GraphQLType("Human", "Droid")`). A subquery processed by the generator (`@GraphQL`) must declare
**exactly one** type. `@GraphQLType` is only valid on a subquery, not on a `GraphQLOperation`.

##### Subquery variables

A subquery that references GraphQL variables **must** declare them in a `type VariableDefs` member,
written in operation-header syntax (the GraphQL spec's `VariablesDefinition`). A subquery that
references none omits it — the member is not part of `GraphQLSubquery`, so there is nothing to write.
It is a declaration only: no runtime value, case class or encoder behind it, unlike an operation's
`Variables`:

```scala
@GraphQLType("Query")
abstract class HeroByEpisode extends GraphQLSubquery.Typed[StarWars, Json] {
  type VariableDefs = "($ep: Episode!)"
  val subquery      = gql"{ hero(episode: $$ep) { name } }"
}
```

(`$` is escaped as `$$` inside `gql`, as in any interpolated string. The `VariableDefs` literal is a
plain string, so it is written with a single `$`.)

The declaration is checked against usage (here `$ep` feeds `hero(episode: Episode!)`); a variable
used but not declared, or declared with an incompatible type, is a scalafix error. Declaring a
variable the subquery doesn't use is currently accepted (unused-variable detection is disabled, see
`validateParsed`). For subqueries processed by the generator (`@GraphQL`), `type VariableDefs` is
**inferred from usage and emitted automatically** when you don't write it; hand-written subqueries
declare it explicitly.

Note that the declaration is what the caller-check below keys on: a subquery that uses a variable
without declaring it reads as "requires nothing" to callers. That omission is an error wherever the
subquery is validated (it needs `@GraphQLType` and a configured schema), so keep validation enabled
on projects that publish subqueries.

To splice a subquery into an operation *and* have the operation's variables checked against it, build
the `document` with the `gql` interpolator (from `clue`) instead of `s`:

```scala
trait MyQuery extends GraphQLOperation[StarWars] {
  val document = gql"query ($$ep: Episode!) $HeroByEpisode"
}
```

`gql` produces exactly the string `s` would, but at **compile time** it verifies the operation
declares every variable required by each spliced subquery, with a compatible ("usable as") type. It
reads the requirement from the subquery's `type VariableDefs` directly, so the check works even when
the subquery comes from a dependency jar; a missing or wrong-typed variable is a compile error.

A subquery splicing another subquery is checked the same way, against its own `VariableDefs` instead
of an operation header:

```scala
@GraphQLType("Character")
object FriendsWithHero extends GraphQLSubquery.Typed[StarWars, Json] {
  type VariableDefs = "($ep: Episode!)"       // required: `HeroByEpisode` needs it
  val subquery      = gql"{ id friends $HeroByEpisode }"
}
```

Because each level has to declare what the level below requires, the requirement reaches the
operation transitively — an operation splicing `FriendsWithHero` must declare `$ep` as well. Nesting
is checked one level at a time; no whole-tree analysis is involved.

The schema location is configured (once) in `.scalafix.conf`:

```hocon
Clue.schemaDirs = ["path/to/schemas"]
```

**With `sbt-clue`:** validation runs automatically **on every compile** — the plugin decorates the
`compile` task to run the validation rule after compilation (disable with
`clueValidateOnCompile := false`), so an invalid hand-written operation/subquery fails the build.
`<project>/clueCheck` runs the same check on demand (e.g. in CI). The generator
separately validates the annotated sources under `src/clue/scala` during generation. A
`@GraphQL`/`@GraphQLSchema`/`@GraphQLStub` annotation found outside the clue source directory is
reported as a **warning** (those are only processed by the generator, so the annotation has no effect
there). (Don't add `rules = [GraphQLGen]` to `.scalafix.conf` — a plain `scalafixAll` would then
expand the annotated generator inputs in place.)

**Without the plugin** (running scalafix directly), use the validation-only `GraphQLValidate` rule
(it reads the same `Clue` config) and run `scalafixAll` / `scalafixCheckAll`:

```hocon
rules = [GraphQLValidate]
Clue.schemaDirs = ["path/to/schemas"]
```

A subquery with no `@GraphQLType` annotation has no declared root type, so it can't be validated —
this is reported as a warning rather than silently skipped.

### 3) Invoke operations

#### Example

``` scala
fetchClient.request(CharacterQuery)(CharacterQuery.Variables("0001"))
  .forEach(println).unsafeRunSync()

# Data(Some(Character("0001", Some("Luke"))))
```

### Tracing with otel4s

The `clue-otel4s` module wraps any clue with OpenTelemetry tracing via [otel4s](https://typelevel.org/otel4s/).

A `SpanKind.Client` span is emitted per HTTP request or subscription.
W3C trace context is automatically propagated to the server:

- **HTTP requests**: the current span's `traceparent` (and `tracestate`) is injected into outgoing
request headers, thus client spans can propagate to the server
- **WebSocket requests**: `traceparent` is included in the `extensions` field, so the server can
create child reading `extensions.traceparent`.

Some span attributes recorded automatically:
* `clue.version`
* `http.request.method`
* `graphql.operation.type`
* `graphql.operation.name`
* `graphql.document`
* `clue.response.hasData`

And on errors `clue.response.hasErrors`, `clue.response.errorCount`, `clue.response.errors`.

W3C propagation requires the SDK to be configured with `W3CTraceContextPropagator`.
See https://ochenashko.com/practical-observability-distributed-tracing/#6-cross-service-propagation
