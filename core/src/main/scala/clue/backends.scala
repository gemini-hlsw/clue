// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.syntax.all.*
import clue.model.GraphQLRequest
import clue.model.StreamingMessage
import io.circe.Encoder

/*
 * One-shot backend.
 */
trait FetchBackend[F[_], P] { // P = Implementation-specific request request params
  def request[V: Encoder](request: GraphQLRequest[V], requestParams: P): F[String]
}

/*
 * Callbacks handling persistent backend events.
 * CE = Close Event, received by client when connection is closed.
 */
trait PersistentBackendHandler[F[_], CE] {
  def onMessage(connectionId: ConnectionId, msg:   String): F[Unit]
  def onClose(connectionId:   ConnectionId, event: CE): F[Unit]
}

/*
 * The connection provided by a persistent backend.
 * CP = Close Parameters, sent by client when close is requested.
 */
trait PersistentConnection[F[_], CP] {
  def send(msg:                                      StreamingMessage.FromClient): F[Unit]
  final def close(closeParameters:                   CP): F[Unit] = closeInternal(closeParameters.some)
  final def close(): F[Unit]                                      = closeInternal(none)
  protected[clue] def closeInternal(closeParameters: Option[CP]): F[Unit]
}

/*
 * Connection oriented backend.
 * CP = Close Parameters, sent by client when close is requested.
 * CE = Close Event, received by client when connection is closed.
 */
trait PersistentBackend[F[_], P, CP, CE] {
  def connect(
    connectionParams: P,
    handler:          PersistentBackendHandler[F, CE],
    connectionId:     ConnectionId
  ): F[PersistentConnection[F, CP]]
}
