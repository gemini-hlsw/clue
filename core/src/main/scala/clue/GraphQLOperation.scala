// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import io.circe.Decoder
import io.circe.Encoder

/*
 * A query, mutation or subscription must extend this trait.
 */
trait GraphQLOperation[S] {
  val document: String
  type Variables
  type Data

  val varEncoder: Encoder.AsObject[Variables]
  val dataDecoder: Decoder[Data]

  object implicits {
    implicit val implicitVarEncoder: Encoder.AsObject[Variables] = varEncoder
    implicit val implicitDataDecoder: Decoder[Data]              = dataDecoder
  }
}
