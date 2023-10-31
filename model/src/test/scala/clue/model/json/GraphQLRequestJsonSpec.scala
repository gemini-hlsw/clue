// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model.json

import clue.ListLimitingDisciplineSuite
import clue.model.GraphQLRequest
import clue.model.arb._
import io.circe.JsonObject
import io.circe.testing.CodecTests
import io.circe.testing.instances._

final class GraphQLRequestJsonSpec extends ListLimitingDisciplineSuite {
  import ArbGraphQLRequest._

  checkAll("GraphQLRequest", CodecTests[GraphQLRequest[JsonObject]].codec)
}
