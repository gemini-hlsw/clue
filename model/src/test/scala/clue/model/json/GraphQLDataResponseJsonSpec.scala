// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model.json

import clue.model.GraphQLDataResponse
import clue.model.arb._
import io.circe.Json
import io.circe.testing.CodecTests
import io.circe.testing.instances._
import munit.DisciplineSuite

final class GraphQLDataResponseJsonSpec extends DisciplineSuite {
  import ArbGraphQLDataResponse._

  checkAll("GraphQLDataResponse", CodecTests[GraphQLDataResponse[Json]].codec)
}
