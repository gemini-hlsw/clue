// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model

import cats.laws.discipline.TraverseTests
import clue.ListLimitingDisciplineSuite
import clue.model.arb.*

final class GraphQLResponseSpec extends ListLimitingDisciplineSuite {
  import ArbGraphQLResponse._

  checkAll(
    "Traversable[GraphQLResponse]",
    TraverseTests[GraphQLResponse].traverse[Int, Int, Int, Int, Option, Option]
  )
}
