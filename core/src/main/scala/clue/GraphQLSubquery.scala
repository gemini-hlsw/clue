// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import io.circe.Decoder

/*
 * A subquery must extend this trait.
 */
trait GraphQLSubquery[S] {
  val subquery: String
  type Data

  val dataDecoder: Decoder[Data]

  object implicits {
    implicit val implicitDataDecoder: Decoder[Data] = dataDecoder
  }
}
