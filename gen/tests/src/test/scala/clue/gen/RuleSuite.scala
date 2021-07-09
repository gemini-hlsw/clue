// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package fix

import scalafix.testkit.AbstractSemanticRuleSuite
import org.scalatest.FunSuiteLike

class RuleSuite extends AbstractSemanticRuleSuite() with FunSuiteLike {
  runAllTests()
}
