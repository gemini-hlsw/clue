// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.gen

import cats._
import cats.kernel.laws.discipline.MonoidTests
import munit._
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen

class AccumulatorSuite extends DisciplineSuite {

  val genAccumulator: Gen[Accumulator[Int, Int, Int]] =
    for {
      classes  <- arbitrary[List[Int]]
      parAccum <- arbitrary[List[Int]]
      sum      <- arbitrary[Option[Int]]
    } yield new Accumulator(classes, parAccum, sum)

  implicit val arbitraryAccumulator: Arbitrary[Accumulator[Int, Int, Int]] = Arbitrary(
    genAccumulator
  )

  implicit val eqAccumulator: Eq[Accumulator[Int, Int, Int]] =
    Eq.by(x => (x.classes, x.parAccum, x.sum))

  checkAll("Monoid[Accumulator]", MonoidTests[Accumulator[Int, Int, Int]].monoid)
}
