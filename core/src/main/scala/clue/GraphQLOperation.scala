// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
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

  object givens {
    given Encoder.AsObject[Variables] = varEncoder
    given Decoder[Data]               = dataDecoder
  }
}

object GraphQLOperation {
  abstract class Typed[S, V: Encoder.AsObject, T: Decoder] extends GraphQLOperation[S] {
    override type Variables = V
    override type Data      = T

    override val varEncoder  = summon[Encoder.AsObject[V]]
    override val dataDecoder = summon[Decoder[T]]
  }

  object Typed {
    abstract class NoInput[S, T: Decoder] extends Typed[S, Unit, T]
  }
}
