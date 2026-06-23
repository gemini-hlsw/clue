// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import io.circe.Decoder

/*
 * A subquery must extend this trait. The GraphQL root type(s) the subquery applies to are declared
 * via the `@clue.annotation.GraphQLType` annotation (used for schema validation), not constructor
 * arguments.
 */
abstract class GraphQLSubquery[S] {
  type Data

  val dataDecoder: Decoder[Data]

  val subquery: String

  object givens {
    given Decoder[Data] = dataDecoder
  }

  final override def toString = subquery
}

object GraphQLSubquery {
  abstract class Typed[S, T: Decoder] extends GraphQLSubquery[S] {
    override type Data = T
    override val dataDecoder = summon[Decoder[T]]
  }
}
