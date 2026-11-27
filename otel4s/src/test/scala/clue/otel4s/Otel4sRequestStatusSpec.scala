// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.otel4s

import cats.data.Ior
import cats.effect.Deferred
import cats.effect.IO
import clue.FetchClientWithPars
import clue.model.GraphQLQuery
import clue.model.GraphQLResponse
import io.circe.Decoder
import io.circe.Json
import io.circe.JsonObject
import munit.CatsEffectSuite
import org.typelevel.otel4s.sdk.testkit.trace.TracesTestkit
import org.typelevel.otel4s.sdk.trace.data.SpanData
import org.typelevel.otel4s.sdk.trace.data.StatusData
import org.typelevel.otel4s.trace.Tracer

class Otel4sRequestStatusSpec extends CatsEffectSuite:

  private type Request = IO[GraphQLResponse[Json]]

  private val doc = GraphQLQuery("query Edits { edits { id } }")

  private val boom = new RuntimeException("boom")

  private def stubFetch(
    started:  IO[Unit],
    response: IO[GraphQLResponse[Json]]
  ): FetchClientWithPars[IO, Unit, Unit] =
    new FetchClientWithPars[IO, Unit, Unit]:
      protected[clue] def requestInternal[D: Decoder](
        document:      GraphQLQuery,
        operationName: Option[String],
        variables:     Option[JsonObject],
        extensions:    Option[JsonObject],
        modParams:     Unit => Unit,
        descriptor:    Option[String]
      ): IO[GraphQLResponse[D]] =
        started >> response.map(_.asInstanceOf[GraphQLResponse[D]])

  private def requestSpan(
    response: IO[GraphQLResponse[Json]],
    run:      (Request, IO[Unit]) => IO[Unit]
  ): IO[SpanData] =
    TracesTestkit
      .inMemory[IO]()
      .use: testkit =>
        for
          tracer  <- testkit.tracerProvider.get("test")
          started <- Deferred[IO, Unit]
          client   =
            given Tracer[IO] = tracer
            Otel4sMiddleware(stubFetch(started.complete(()).void, response))
          _       <- run(client.requestInternal[Json](doc, None, None, None, identity, None), started.get)
          spans   <- testkit.finishedSpans
        yield spans
      .map:
        case List(span) => span
        case other      => fail(s"expected one span, got [$other]")

  private def exitCase(span: SpanData): Option[String] =
    span.attributes.elements.get[String]("clue.exitCase").map(_.value)

  private def exceptionEvents(span: SpanData): Int =
    span.events.elements.count(_.name == "exception")

  test("a request cancelled while in flight is recorded but not an error"):
    val cancel: (Request, IO[Unit]) => IO[Unit] = (request, started) =>
      request.start.flatMap: fiber =>
        started >> fiber.cancel
    requestSpan(IO.never, cancel).map: span =>
      assertEquals(span.status, StatusData.Unset)
      assertEquals(exitCase(span), Some("Canceled"))
      assertEquals(exceptionEvents(span), 0)

  test("a request that succeeds leaves the span status unset"):
    val ok: IO[GraphQLResponse[Json]] = IO.pure(GraphQLResponse(Ior.right(Json.obj())))
    requestSpan(ok, (request, _) => request.void).map: span =>
      assertEquals(span.status, StatusData.Unset)
      assertEquals(exitCase(span), None)

  test("a request that fails marks the span as an error with the failure message"):
    requestSpan(IO.raiseError(boom), (request, _) => request.void.voidError).map: span =>
      assertEquals(span.status, StatusData.Error(Some("boom")))
      assertEquals(exceptionEvents(span), 1)

end Otel4sRequestStatusSpec
