// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.js

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import clue.HttpStatusException
import clue.model.GraphQLQuery
import clue.model.GraphQLRequest
import io.circe.Json
import org.scalajs.dom

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.scalajs.js

class FetchJsBackendRequestSuite extends munit.FunSuite {

  private given ExecutionContext = munitExecutionContext

  private val SpecAccept = "application/graphql-response+json, application/json;q=0.9"

  private val graphQLRequest: GraphQLRequest[Json] =
    GraphQLRequest(GraphQLQuery("query { id }"))

  private val originalFetch: js.Dynamic = js.Dynamic.global.fetch

  private var lastInit: js.Dynamic = null

  override def afterEach(context: AfterEach): Unit = {
    js.Dynamic.global.fetch = originalFetch
    lastInit = null
  }

  // Replace the global `fetch` with a stub. The stub records the request and answers with the given
  // status, content type and body.
  private def stubFetch(status: Int, contentType: Option[String], body: String): Unit = {
    val stub: js.Function2[js.Any, js.Dynamic, js.Promise[dom.Response]] =
      (_, init) => {
        lastInit = init
        val headers      = js.Dictionary.empty[String]
        contentType.foreach(ct => headers("Content-Type") = ct)
        val responseInit = js.Dynamic
          .literal(status = status, headers = headers.asInstanceOf[js.Any])
          .asInstanceOf[dom.ResponseInit]
        js.Promise.resolve[dom.Response](new dom.Response(body, responseInit))
      }
    js.Dynamic.global.fetch = stub.asInstanceOf[js.Dynamic]
  }

  private def send(
    method:  FetchMethod = FetchMethod.POST,
    headers: dom.Headers = new dom.Headers()
  ): Future[Either[Throwable, String]] =
    FetchJsBackend[IO](method)
      .request(graphQLRequest, FetchJsRequest("https://example.com/graphql", headers))
      .attempt
      .unsafeToFuture()

  private def sentHeader(name: String): Option[String] =
    Option(lastInit.headers.asInstanceOf[dom.Headers].get(name))

  test("request sends the Accept header of the specification") {
    stubFetch(200, "application/json".some, """{"data":{}}""")
    send().map(_ => assertEquals(sentHeader("Accept"), SpecAccept.some))
  }

  test("request keeps an Accept header that the caller set") {
    stubFetch(200, "application/json".some, """{"data":{}}""")
    val headers = new dom.Headers()
    headers.set("Accept", "application/json")
    send(headers = headers).map(_ => assertEquals(sentHeader("Accept"), "application/json".some))
  }

  test("request returns the body of a 400 response with the GraphQL media type") {
    val body = """{"errors":[{"message":"Bad field"}]}"""
    stubFetch(400, "application/graphql-response+json; charset=utf-8".some, body)
    send().map(assertEquals(_, body.asRight))
  }

  test("request returns the body of a 200 response with the legacy JSON media type") {
    val body = """{"data":{"id":1}}"""
    stubFetch(200, "application/json".some, body)
    send().map(assertEquals(_, body.asRight))
  }

  test("request raises HttpStatusException for a 400 response with the legacy JSON media type") {
    val body = """{"errors":[{"message":"Bad field"}]}"""
    stubFetch(400, "application/json".some, body)
    send().map {
      case Left(HttpStatusException(status, contentType, seenBody)) =>
        assertEquals(status, 400)
        assertEquals(contentType, "application/json".some)
        assertEquals(seenBody, body)
      case other                                                    =>
        fail(s"Expected HttpStatusException, got [$other]")
    }
  }

  test("request raises HttpStatusException for a 502 response with an HTML body") {
    stubFetch(502, "text/html".some, "<html>Bad Gateway</html>")
    send().map {
      case Left(e: HttpStatusException) => assertEquals(e.status, 502)
      case other                        => fail(s"Expected HttpStatusException, got [$other]")
    }
  }
}
