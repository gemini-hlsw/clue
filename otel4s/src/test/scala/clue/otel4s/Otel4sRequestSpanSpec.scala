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
 *
 * `Otel4sStreamingClient extends Otel4sFetchClient`, so it used to inherit `requestInternal`
 * wholesale — including the hardcoded `http.request.method: POST` attribute. That mislabelled
 * queries/mutations sent as WebSocket `Subscribe` frames as HTTP POST. These tests pin the fix:
 * the HTTP client reports the method, the streaming client does not.
 *
 * Only the pure attribute lists are inspected, so a noop tracer suffices and no request is ever
 * actually run (the wrapped clients' methods are never called).
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
      ): IO[GraphQLResponse[D]] =
        IO.never
      protected[clue] def subscribeInternal[D: Decoder](
        document:      GraphQLQuery,
        operationName: Option[String],
        variables:     Option[JsonObject],
        extensions:    Option[JsonObject],
        descriptor:    Option[String]
      ): Resource[IO, Stream[IO, GraphQLResponse[D]]] =
        Resource.eval(IO.never)

  private def httpMethodKeys[S](client: Otel4sFetchClient[IO, Unit, S]): List[String] =
    client.requestTransportAttributes.map(_.key.name)

  test("the HTTP fetch client tags requests with http.request.method: POST"):
    val client = new Otel4sFetchClient[IO, Unit, Unit](stubFetch, noMod, noAttrs)
    assert(httpMethodKeys(client).contains("http.request.method"))

  test("the streaming (WebSocket) client does NOT tag requests with http.request.method"):
    // Regression: Otel4sStreamingClient extends Otel4sFetchClient and used to inherit the POST
    // attribute, mislabeling WebSocket Subscribe frames as HTTP POST.
    val client = new Otel4sStreamingClient[IO, Unit](stubStream, noMod, noAttrs)
    assert(!httpMethodKeys(client).contains("http.request.method"))

end Otel4sRequestSpanSpec
