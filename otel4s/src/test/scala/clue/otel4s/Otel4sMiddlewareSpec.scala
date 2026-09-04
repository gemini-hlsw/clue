// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.otel4s

import clue.model.GraphQLQuery
import io.circe.Json
import io.circe.JsonObject
import munit.FunSuite
import org.typelevel.otel4s.Attribute

class Otel4sMiddlewareSpec extends FunSuite:

  private val doc = GraphQLQuery(
    "query ObservationVisits($id: ID!) { observation(id: $id) { id } }"
  )

  private def attribute(attrs: List[Attribute[?]], key: String): Option[String] =
    attrs.collectFirst { case a if a.key.name == key => a.value.toString }

  test("commonAttributes reports the operation type read from the document") {
    assertEquals(
      attribute(Otel4sMiddleware.commonAttributes(doc, None, None), "graphql.operation.type"),
      Some("query")
    )
  }

  test("commonAttributes keeps operationName and the descriptor separate") {
    // `operationName` goes on the wire; the descriptor is tracing-only. They are distinct
    // attributes and neither implies the other.
    val attrs = Otel4sMiddleware.commonAttributes(doc, Some("ObservationVisits"), Some("ObsQuery"))
    assertEquals(attribute(attrs, "graphql.operation.name"), Some("ObservationVisits"))
    assertEquals(attribute(attrs, "clue.descriptor"), Some("ObsQuery"))
  }

  test("commonAttributes omits clue.descriptor when no descriptor is set") {
    val attrs = Otel4sMiddleware.commonAttributes(doc, None, None)
    assert(!attrs.exists(_.key.name == "clue.descriptor"),
           "did not expect a clue.descriptor attribute"
    )
  }

  test("spanName prefers the descriptor over the document's own name") {
    assertEquals(
      Otel4sMiddleware.spanName("request", doc, Some("ObsQuery")),
      "clue-request-ObsQuery"
    )
  }

  test("spanName falls back to the document's operation name") {
    assertEquals(
      Otel4sMiddleware.spanName("request", doc, None),
      "clue-request-query-ObservationVisits"
    )
  }

  test("requestBodySize reflects the serialized payload and grows with variables") {
    // The serialized request is JSON wrapping the document, so its length must at least contain
    // the document text …
    val base     = Otel4sMiddleware.requestBodySize(doc, Some("ObservationVisits"), None, None)
    assert(base >= doc.value.length.toLong)
    // … and adding variables can only make it larger.
    val withVars = Otel4sMiddleware.requestBodySize(
      doc,
      Some("ObservationVisits"),
      Some(JsonObject("id" -> Json.fromString("o-1"))),
      None
    )
    assert(withVars > base)
  }

end Otel4sMiddlewareSpec
