// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.otel4s

import cats.effect.IO
import cats.effect.Resource
import clue.FetchClientWithPars
import clue.StreamingClient
import clue.model.GraphQLQuery
import clue.model.GraphQLResponse
import fs2.Stream
import io.circe.Decoder
import io.circe.JsonObject
import munit.FunSuite
import org.typelevel.otel4s.trace.Tracer

/**
 * Regression coverage for the transport-specific attributes on the `request` span.
 */
class Otel4sRequestSpanSpec extends FunSuite:

  given Tracer[IO] = Tracer.noop[IO]

  private def noMod: Otel4sMiddleware.SpanMod[IO] = identity

  private def noAttrs: (GraphQLQuery, Option[JsonObject]) => IO[List[Nothing]] =
    (_, _) => IO.pure(Nil)

  private def stubFetch: FetchClientWithPars[IO, Unit, Unit] =
    new FetchClientWithPars[IO, Unit, Unit]:
      protected[clue] def requestInternal[D: Decoder](
        document:      GraphQLQuery,
        operationName: Option[String],
        variables:     Option[JsonObject],
        extensions:    Option[JsonObject],
        modParams:     Unit => Unit,
        descriptor:    Option[String]
      ): IO[GraphQLResponse[D]] =
        IO.never

  private def stubStream: StreamingClient[IO, Unit] =
    new StreamingClient[IO, Unit]:
      protected[clue] def requestInternal[D: Decoder](
        document:      GraphQLQuery,
        operationName: Option[String],
        variables:     Option[JsonObject],
        extensions:    Option[JsonObject],
        modParams:     Unit => Unit,
        descriptor:    Option[String]
      ): IO[GraphQLResponse[D]]                       =
        IO.never
      protected[clue] def subscribeInternal[D: Decoder](
        document:      GraphQLQuery,
        operationName: Option[String],
        variables:     Option[JsonObject],
        extensions:    Option[JsonObject],
        descriptor:    Option[String]
      ): Resource[IO, Stream[IO, GraphQLResponse[D]]] =
        Resource.eval(IO.never)

  private def transportKeys(client: Any): List[String] =
    client match
      case c: Otel4sFetchClient[?, ?, ?] => c.transportAttributes.map(_.key.name)
      case other                         => fail(s"expected an Otel4sFetchClient, got [$other]")

  test("the HTTP fetch client tags requests with http.request.method"):
    assert(transportKeys(Otel4sMiddleware(stubFetch)).contains("http.request.method"))

  test("the streaming (WebSocket) client does NOT tag requests with http.request.method"):
    assert(!transportKeys(Otel4sMiddleware(stubStream)).contains("http.request.method"))

  test("a directly constructed client claims no transport by default"):
    val client = new Otel4sFetchClient[IO, Unit, Unit](stubFetch, noMod, noAttrs)
    assertEquals(client.transportAttributes, Nil)

end Otel4sRequestSpanSpec
