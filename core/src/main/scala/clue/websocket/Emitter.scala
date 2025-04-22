// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.websocket

import clue.model.*
import io.circe.*

// Internal structure to emit data and errors to the client.
protected[clue] trait Emitter[F[_]] {
  val request: GraphQLRequest[JsonObject]

  def emitData(response:        GraphQLResponse[Json]): F[Unit]
  def emitGraphQLErrors(errors: GraphQLErrors): F[Unit]
  def crash(t:                  Throwable): F[Unit]
  val halt: F[Unit]
}
