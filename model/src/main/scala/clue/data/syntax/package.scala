// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.data

import monocle.PLens
import monocle.POptional
import clue.data.optics

package object syntax {
  implicit final class AnyToInputOps[A](private val a: A) extends AnyVal {
    def assign: Input[A] = Assign(a)
  }

  implicit final class AnyOptionToInputOps[A](private val a: Option[A]) extends AnyVal {
    def orIgnore: Input[A]   = Input.orIgnore(a)
    def orUnassign: Input[A] = Input.orUnassign(a)
  }

  implicit final class LensToLensOps[S, T, A, B](private val lens: PLens[S, T, A, B])
      extends AnyVal {
    // Copied verbatim from monocle, which has it as a private member
    private def adapt[A1, B1](implicit evA: A =:= A1, evB: B =:= B1): PLens[S, T, A1, B1] =
      evB.substituteCo[PLens[S, T, A1, *]](evA.substituteCo[PLens[S, T, *, B]](lens))

    def assign[A1, B1](implicit
      ev1: A =:= Input[A1],
      ev2: B =:= Input[B1]
    ): POptional[S, T, A1, B1] =
      adapt[Input[A1], Input[B1]].andThen(optics.pAssign[A1, B1])
  }
}
