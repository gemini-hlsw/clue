// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import io.circe.Decoder
import io.circe.Encoder

trait GraphQLQuery {
  val document: String
  type Variables
  type Data

  implicit val varEncoder: Encoder[Variables]
  implicit val dataDecoder: Decoder[Data]
}
