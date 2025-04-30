// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import io.circe.Decoder

/*
 * A subquery must extend this trait.
 */
abstract class GraphQLSubquery[S](val rootType: String) {
  type Data

  val dataDecoder: Decoder[Data]

  val subquery: String

  object implicits {
    implicit val implicitDataDecoder: Decoder[Data] = dataDecoder
  }

  final override def toString = subquery
}

object GraphQLSubquery {
  abstract class Typed[S, T: Decoder](rootType: String) extends GraphQLSubquery[S](rootType) {
    override type Data = T
    override val dataDecoder = implicitly[Decoder[T]]
  }
}
