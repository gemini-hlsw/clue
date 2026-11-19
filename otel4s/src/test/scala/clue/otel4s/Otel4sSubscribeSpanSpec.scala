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
import org.typelevel.otel4s.sdk.trace.data.SpanData
import org.typelevel.otel4s.sdk.trace.data.StatusData
import org.typelevel.otel4s.trace.Tracer

class Otel4sSubscribeSpanSpec extends CatsEffectSuite:

  private type Subscription = Resource[IO, Stream[IO, GraphQLResponse[Json]]]

  private val doc = GraphQLQuery("subscription Edits { edits { id } }")

  private val boom = new RuntimeException("boom")

  private def stubStream(events: Stream[IO, Nothing]): StreamingClient[IO, Unit] =
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
        Resource.pure(events)

  private def subscribeSpan(
    events: Stream[IO, Nothing],
    run:    Subscription => IO[Unit]
  ): IO[SpanData] =
    TracesTestkit
      .inMemory[IO]()
      .use: testkit =>
        for
          tracer <- testkit.tracerProvider.get("test")
          client  =
            given Tracer[IO] = tracer
            Otel4sStreamingClient[IO, Unit](stubStream(events), identity, (_, _) => IO.pure(Nil))
          _      <- run(client.subscribeInternal[Json](doc, None, None, None, None))
          spans  <- testkit.finishedSpans
        yield spans
      .map:
        case List(span) => span
        case other      => fail(s"expected one span, got [$other]")

  private def exitCase(span: SpanData): Option[String] =
    span.attributes.elements.get[String]("clue.exitCase").map(_.value)

  private def exceptionEvents(span: SpanData): Int =
    span.events.elements.count(_.name == "exception")

  test("a subscription cancelled before the stream is pulled is recorded but not an error"):
    val cancel: Subscription => IO[Unit] = sub =>
      Deferred[IO, Unit].flatMap: started =>
        sub
          .use(_ => started.complete(()) >> IO.never)
          .start
          .flatMap: fiber =>
            started.get >> fiber.cancel
    subscribeSpan(Stream.never[IO], cancel).map: span =>
      assertEquals(span.status, StatusData.Unset)
      assertEquals(exitCase(span), Some("Canceled"))

  test("a subscription whose stream ends normally records a plain Succeeded exit case"):
    subscribeSpan(Stream.empty, _.use(_.compile.drain)).map: span =>
      assertEquals(span.status, StatusData.Unset)
      assertEquals(exitCase(span), Some("Succeeded"))

  test("a consumer failure marks the span as an error with the failure message"):
    subscribeSpan(Stream.never[IO], _.use(_ => IO.raiseError(boom)).voidError).map: span =>
      assertEquals(span.status, StatusData.Error(Some("boom")))
      assertEquals(exceptionEvents(span), 1)

  test("a stream failure marks the span as an error with the failure message"):
    subscribeSpan(Stream.raiseError[IO](boom), _.use(_.compile.drain).voidError).map: span =>
      assertEquals(span.status, StatusData.Error(Some("boom")))
      assertEquals(exitCase(span), Some("Errored"))
      assertEquals(exceptionEvents(span), 1)

  test("a stream failure the consumer recovers from still records the exception"):
    val recover: Subscription => IO[Unit] =
      _.use(_.handleErrorWith(_ => Stream.empty).compile.drain)
    subscribeSpan(Stream.raiseError[IO](boom), recover).map: span =>
      assertEquals(span.status, StatusData.Error(Some("boom")))
      assertEquals(exceptionEvents(span), 1)

end Otel4sSubscribeSpanSpec
