// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.data.arb

import clue.data._
import org.scalacheck._

import Arbitrary._
import Gen._

trait ArbInput {
  implicit def arbInput[A: Arbitrary]: Arbitrary[Input[A]] =
    Arbitrary(
      oneOf(
        Gen.const(Ignore),
        Gen.const(Unassign),
        arbitrary[A].map(Assign.apply)
      )
    )

  implicit def arbInputF[A](implicit fArb: Arbitrary[A => A]): Arbitrary[Input[A] => Input[A]] =
    Arbitrary(
      arbitrary[A => A].map(f => _.map(f))
    )
}

object ArbInput extends ArbInput
