// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.data

import monocle.PPrism
import monocle.Prism

trait InputOptics {
  final def pAssign[A, B]: PPrism[Input[A], Input[B], A, B] =
    PPrism[Input[A], Input[B], A, B] {
      case Assign(a) => Right(a)
      case Ignore    => Left(Ignore)
      case Unassign  => Left(Unassign)
    }(Assign.apply)

  final def assign[A]: Prism[Input[A], A] =
    pAssign[A, A]

  final def ignore[A]: Prism[Input[A], Unit] =
    Prism[Input[A], Unit] { case Ignore => Some(()); case _ => None }(_ => Ignore)

  final def unassign[A]: Prism[Input[A], Unit] =
    Prism[Input[A], Unit] { case Unassign => Some(()); case _ => None }(_ => Unassign)
}

object optics extends InputOptics
