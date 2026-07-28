// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.otel4s

import clue.model.GraphQLQuery
import munit.FunSuite

class Otel4sMiddlewareSpec extends FunSuite:

  // Anonymous document: descriptor is the only way to name it well.
  private val doc = GraphQLQuery(
    "query ObservationVisits($id: ID!) { observation(id: $id) { id } }"
  )

  test("commonAttributes emits clue.descriptor when a descriptor is set") {
    val attrs      = Otel4sMiddleware.commonAttributes(doc, None, Some("ObservationVisits"))
    val descriptor = attrs.find(_.key.name == "clue.descriptor")
    assert(descriptor.isDefined, "expected a clue.descriptor attribute")
    assertEquals(descriptor.get.value: Any, "ObservationVisits")
  }

  test("commonAttributes omits clue.descriptor when no descriptor is set") {
    val attrs = Otel4sMiddleware.commonAttributes(doc, None, None)
    assert(!attrs.exists(_.key.name == "clue.descriptor"),
           "did not expect a clue.descriptor attribute"
    )
  }

  test("commonAttributes still emits the graphql document regardless of descriptor") {
    val attrs = Otel4sMiddleware.commonAttributes(doc, None, Some("X"))
    assert(attrs.exists(_.key.name == "graphql.document"), "expected a graphql.document attribute")
  }

end Otel4sMiddlewareSpec
