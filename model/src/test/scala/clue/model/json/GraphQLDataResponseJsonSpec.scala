// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model.json

import clue.ListLimitingDisciplineSuite
import clue.model.GraphQLDataResponse
import clue.model.arb.ArbGraphQLDataResponse
import io.circe.Json
import io.circe.testing.CodecTests
import io.circe.testing.instances.*

final class GraphQLDataResponseJsonSpec extends ListLimitingDisciplineSuite:
  import ArbGraphQLDataResponse.given

  checkAll("GraphQLDataResponse", CodecTests[GraphQLDataResponse[Json]].codec)
