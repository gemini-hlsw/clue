// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.otel4s

import cats.effect.Deferred
import cats.effect.IO
import cats.effect.Resource
import clue.StreamingClient
import clue.model.GraphQLQuery
import clue.model.GraphQLResponse
import fs2.Stream
import io.circe.Decoder
import io.circe.Json
import io.circe.JsonObject
import munit.CatsEffectSuite
import org.typelevel.otel4s.sdk.testkit.trace.TracesTestkit
import org.typelevel.otel4s.sdk.trace.data.StatusData
import org.typelevel.otel4s.trace.Tracer

class Otel4sSubscribeSpanSpec extends CatsEffectSuite:

  private type Subscription = Resource[IO, Stream[IO, GraphQLResponse[Json]]]

  private val doc = GraphQLQuery("subscription Edits { edits { id } }")

  private val stubStream: StreamingClient[IO, Unit] =
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
        Resource.pure(Stream.never[IO])

  private def subscribeStatus(run: Subscription => IO[Unit]): IO[StatusData] =
    TracesTestkit
      .inMemory[IO]()
      .use: testkit =>
        for
          tracer <- testkit.tracerProvider.get("test")
          client  =
            given Tracer[IO] = tracer
            Otel4sStreamingClient[IO, Unit](stubStream, identity, (_, _) => IO.pure(Nil))
          _      <- run(client.subscribeInternal[Json](doc, None, None, None, None))
          spans  <- testkit.finishedSpans
        yield spans.map(_.status)
      .map:
        case List(status) => status
        case other        => fail(s"expected one span, got [$other]")

  test("a cancelled subscription leaves the span status unset"):
    val cancel: Subscription => IO[Unit] = sub =>
      Deferred[IO, Unit].flatMap: started =>
        sub
          .use(_ => started.complete(()) >> IO.never)
          .start
          .flatMap: fiber =>
            started.get >> fiber.cancel
    assertIO(subscribeStatus(cancel), StatusData.Unset)

  test("a failed subscription marks the span as an error with the failure message"):
    val boom: Subscription => IO[Unit] =
      _.use(_ => IO.raiseError(new RuntimeException("boom"))).voidError
    assertIO(subscribeStatus(boom), StatusData.Error(Some("boom")))

end Otel4sSubscribeSpanSpec
