// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.macros

import cats._

/**
 *  Holds the aggregated `CaseClass`es, their `ClassParam`s and possible `Sum` info as we recurse the query AST.
 *
 * `parAccum` accumulates parameters until we have a whole case class definition.
 */
class Accumulator[C, CP, S](
  val classes:  List[C] = List.empty,
  val parAccum: List[CP] = List.empty,
  val sum:      Option[S] = None
)

trait AccumulatorImplicits {
  implicit def monoidAccumulator[C, CP, S]: Monoid[Accumulator[C, CP, S]] =
    new Monoid[Accumulator[C, CP, S]] {
      override def empty: Accumulator[C, CP, S] =
        new Accumulator[C, CP, S]()
      override def combine(
        x: Accumulator[C, CP, S],
        y: Accumulator[C, CP, S]
      ): Accumulator[C, CP, S] =
        new Accumulator[C, CP, S](x.classes ++ y.classes,
                                  x.parAccum ++ y.parAccum,
                                  x.sum.orElse(y.sum)
        )
    }
}

object Accumulator extends AccumulatorImplicits
