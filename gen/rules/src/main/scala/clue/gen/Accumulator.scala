// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.gen

import cats.*

/**
 * Holds the aggregated `CaseClass`es, their `ClassParam`s and possible `Sum` info as we recurse the
 * query AST.
 *
 * `parAccum` accumulates parameters until we have a whole case class definition, then moves it to
 * an element of `classes`.
 */
case class Accumulator[C, CP, S](
  classes:  List[C] = List.empty,
  parAccum: List[CP] = List.empty,
  sum:      Option[S] = None
)

trait AccumulatorImplicits {
  implicit def monoidAccumulator[C, CP, S]: Monoid[Accumulator[C, CP, S]] =
    new Monoid[Accumulator[C, CP, S]] {
      override def empty: Accumulator[C, CP, S] =
        Accumulator[C, CP, S]()
      override def combine(
        x: Accumulator[C, CP, S],
        y: Accumulator[C, CP, S]
      ): Accumulator[C, CP, S] =
        Accumulator[C, CP, S](x.classes ++ y.classes, x.parAccum ++ y.parAccum, x.sum.orElse(y.sum))
    }
}

object Accumulator extends AccumulatorImplicits
