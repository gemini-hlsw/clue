package clue

import cats.syntax.all._
import clue.model.GraphQLRequest
import clue.model.StreamingMessage
import sttp.model.Uri

/*
 * One-shot backend.
 */
trait TransactionalBackend[F[_]] {
  def request(uri: Uri, request: GraphQLRequest): F[String]
}

object TransactionalBackend {
  def apply[F[_]: TransactionalBackend]: TransactionalBackend[F] = implicitly
}

/*
 * Callbacks handling persistent backend events.
 * CE = Close Event, received by client when connection is closed.
 */
trait PersistentBackendHandler[F[_], CE] {
  def onMessage(msg: String): F[Unit]
  def onClose(event: CE): F[Unit]
}

/*
 * The connection provided by a persistent backend.
 * CP = Close Parameters, sent by client when close is requested.
 */
trait PersistentConnection[F[_], CP] {
  def send(msg:                    StreamingMessage.FromClient): F[Unit]
  final def close(closeParameters: CP): F[Unit] = closeInternal(closeParameters.some)
  final def close(): F[Unit] = closeInternal(none)
  protected[clue] def closeInternal(closeParameters: Option[CP]): F[Unit]
}

/*
 * Connection oriented backend.
 * CP = Close Parameters, sent by client when close is requested.
 * CE = Close Event, received by client when connection is closed.
 */
trait PersistentBackend[F[_], CP, CE] {
  def connect(
    uri:     Uri,
    handler: PersistentBackendHandler[F, CE]
  ): F[PersistentConnection[F, CP]]
}

object PersistentBackend {
  def apply[F[_], CP, CE](implicit
    backend: PersistentBackend[F, CP, CE]
  ): PersistentBackend[F, CP, CE] = backend
}
