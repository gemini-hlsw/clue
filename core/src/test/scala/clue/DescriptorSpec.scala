// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.effect.IO
import cats.effect.Ref
import cats.effect.Resource
import cats.syntax.all.*
import clue.model.GraphQLQuery
import clue.model.GraphQLResponse
import io.circe.Decoder
import io.circe.Json
import io.circe.JsonObject
import munit.CatsEffectSuite

/**
 * The descriptor is tracing-only: it never goes on the wire. The only thing that carries it from
 * the call site to a tracing middleware is the `descriptor` parameter of `requestInternal` /
 * `subscribeInternal`, so these tests capture what a wrapped client actually receives.
 */
class DescriptorSpec extends CatsEffectSuite:

  private object Op extends GraphQLOperation.Typed[Unit, JsonObject, Json]:
    val document = "query NamedOp { field }"

  // A client that records the `descriptor` it was handed and answers with an empty response.
  private class Recorder(ref: Ref[IO, Option[Option[String]]]) extends StreamingClient[IO, Unit]:
    protected[clue] def requestInternal[D: Decoder](
      document:      GraphQLQuery,
      operationName: Option[String],
      variables:     Option[JsonObject],
      extensions:    Option[JsonObject],
      modParams:     Unit => Unit,
      descriptor:    Option[String]
    ): IO[GraphQLResponse[D]] =
      ref.set(descriptor.some) *> IO.raiseError(new NoSuchElementException("no data"))

    protected[clue] def subscribeInternal[D: Decoder](
      document:      GraphQLQuery,
      operationName: Option[String],
      variables:     Option[JsonObject],
      extensions:    Option[JsonObject],
      descriptor:    Option[String]
    ): Resource[IO, fs2.Stream[IO, GraphQLResponse[D]]] =
      Resource.eval(ref.set(descriptor.some).as(fs2.Stream.empty))

  // Runs `f` against a recording client and returns the descriptor it saw. The client's response is
  // an error, which is irrelevant here and discarded: only the recorded value is under test.
  private def descriptorSeen(f: StreamingClient[IO, Unit] => IO[Unit]): IO[Option[String]] =
    for
      ref  <- IO.ref(Option.empty[Option[String]])
      _    <- f(Recorder(ref)).attempt
      seen <- ref.get
    yield seen.getOrElse(fail("the wrapped client was never called"))

  test("withDescriptor reaches requestInternal"):
    assertIO(
      descriptorSeen(_.request(Op).withDescriptor("MyQuery").withInput(JsonObject.empty).void),
      "MyQuery".some
    )

  test("withDescriptor reaches requestInternal through the no-input path"):
    assertIO(descriptorSeen(_.request(Op).withDescriptor("MyQuery").apply.void), "MyQuery".some)

  test("a request without a descriptor passes none"):
    assertIO(descriptorSeen(_.request(Op).withInput(JsonObject.empty).void), none)

  test("withDescriptor reaches subscribeInternal"):
    assertIO(
      descriptorSeen(
        _.subscribe(Op).withDescriptor("MySub").withInput(JsonObject.empty).use_
      ),
      "MySub".some
    )

end DescriptorSpec
