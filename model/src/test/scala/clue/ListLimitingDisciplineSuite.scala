// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import munit.DisciplineSuite
import org.scalacheck

trait ListLimitingDisciplineSuite extends DisciplineSuite {
  override lazy val scalaCheckTestParameters = scalacheck.Test.Parameters.default.withMaxSize(10)
}
