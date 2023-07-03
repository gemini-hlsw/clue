// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package fix

import scalafix.testkit.AbstractSemanticRuleSuite
import org.scalatest.Args
import org.scalatest.funsuite.AnyFunSuiteLike

class RuleSuite extends AbstractSemanticRuleSuite() with AnyFunSuiteLike {
  // run is defined in both AbstractSemanticRuleSuite and FunSuiteLike
  // Scala 3 requires us to explicitly specify which impl to use
  override def run(testName: Option[String], args: Args) =
    super[AbstractSemanticRuleSuite].run(testName, args)

  runAllTests()
}
