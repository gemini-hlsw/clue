// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model.json

import clue.ListLimitingDisciplineSuite
import clue.model.GraphQLError
import clue.model.GraphQLErrors
import clue.model.arb.*
import io.circe.testing.CodecTests
import io.circe.testing.instances.*

final class GraphQLErrorJsonSpec extends ListLimitingDisciplineSuite:
  import ArbGraphQLError.given

  checkAll("GraphQLError.PathElement", CodecTests[GraphQLError.PathElement].codec)
  checkAll("GraphQLError.Location", CodecTests[GraphQLError.Location].codec)
  checkAll("GraphQLError", CodecTests[GraphQLError].codec)
  checkAll("GraphQLErrors", CodecTests[GraphQLErrors].codec)
