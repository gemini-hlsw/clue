// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.http4s

import cats.effect.*
import cats.syntax.all.*
import clue.HttpStatusException
import clue.model.GraphQLQuery
import clue.model.GraphQLRequest
import io.circe.Json
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.client.Client
import org.http4s.headers.Accept
import org.http4s.headers.`Content-Type`
import org.http4s.syntax.literals.*

class Http4sHttpBackendSuite extends CatsEffectSuite:

  private val SpecAccept = "application/graphql-response+json, application/json;q=0.9"

  private val GraphQLResponseMediaType =
    new MediaType("application", "graphql-response+json", compressible = true)

  private val JsonContentType    = `Content-Type`(MediaType.application.json)
  private val GraphQLContentType = `Content-Type`(GraphQLResponseMediaType, Charset.`UTF-8`)
  private val HtmlContentType    = `Content-Type`(MediaType.text.html)

  private val graphQLRequest: GraphQLRequest[Json] =
    GraphQLRequest(GraphQLQuery("query { id }"))

  private val baseRequest: Request[IO] =
    Request[IO](Method.POST, uri"https://example.com/graphql")

  // A client that always answers with the given status, content type and body, and records the
  // request that it received.
  private def stubClient(
    status:      Status,
    contentType: Option[`Content-Type`],
    body:        String,
    sent:        Ref[IO, Option[Request[IO]]]
  ): Client[IO] =
    Client[IO] { request =>
      val response = Response[IO](status = status)
        .withBodyStream(fs2.Stream.emits(body.getBytes("UTF-8")).covary[IO])
      Resource
        .eval(sent.set(request.some))
        .as(contentType.fold(response)(ct => response.putHeaders(ct)))
    }

  private def run(
    status:      Status,
    contentType: Option[`Content-Type`],
    body:        String,
    request:     Request[IO] = baseRequest
  ): IO[(String, Request[IO])] =
    for
      sent   <- Ref.of[IO, Option[Request[IO]]](none)
      result <- Http4sHttpBackend[IO](stubClient(status, contentType, body, sent))
                  .request(graphQLRequest, request)
      seen   <- sent.get
    yield (result, seen.get)

  private def acceptOf(request: Request[IO]): Option[String] =
    request.headers.get[Accept].map(Header[Accept].value)

  test("request sends POST, also when the base request uses another method") {
    run(
      Status.Ok,
      JsonContentType.some,
      """{"data":{}}""",
      Request[IO](Method.GET, uri"https://example.com/graphql")
    ).map((_, sent) => assertEquals(sent.method, Method.POST))
  }

  test("request sends the Accept header of the specification") {
    run(Status.Ok, JsonContentType.some, """{"data":{}}""")
      .map((_, sent) => assertEquals(acceptOf(sent), SpecAccept.some))
  }

  test("request keeps an Accept header that the caller set") {
    run(
      Status.Ok,
      JsonContentType.some,
      """{"data":{}}""",
      baseRequest.putHeaders(Accept(MediaType.application.json.withQValue(QValue.One)))
    ).map((_, sent) => assertEquals(acceptOf(sent), "application/json".some))
  }

  test("request returns the body of a 400 response with the GraphQL media type") {
    val body = """{"errors":[{"message":"Bad field"}]}"""
    run(Status.BadRequest, GraphQLContentType.some, body)
      .map((result, _) => assertEquals(result, body))
  }

  test("request returns the body of a 200 response with the legacy JSON media type") {
    val body = """{"data":{"id":1}}"""
    run(Status.Ok, JsonContentType.some, body).map((result, _) => assertEquals(result, body))
  }

  test("request raises HttpStatusException for a 400 response with the legacy JSON media type") {
    val body = """{"errors":[{"message":"Bad field"}]}"""
    run(Status.BadRequest, JsonContentType.some, body).attempt.map:
      case Left(HttpStatusException(status, contentType, seenBody)) =>
        assertEquals(status, 400)
        assertEquals(contentType, "application/json".some)
        assertEquals(seenBody, body)
      case other                                                    =>
        fail(s"Expected HttpStatusException, got [$other]")
  }

  test("request raises HttpStatusException for a 502 response with an HTML body") {
    run(Status.BadGateway, HtmlContentType.some, "<html>Bad Gateway</html>").attempt.map:
      case Left(e: HttpStatusException) => assertEquals(e.status, 502)
      case other                        => fail(s"Expected HttpStatusException, got [$other]")
  }
