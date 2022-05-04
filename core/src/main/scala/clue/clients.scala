// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.Applicative
import cats.MonadError
import cats.effect.Resource
import cats.syntax.all._
import io.circe._
import io.circe.syntax._
import org.http4s.Headers
import org.http4s.Uri
import org.typelevel.log4cats.Logger

/**
 * A client that allows one-shot queries and mutations.
 */
trait TransactionalClient[F[_], S] {

  case class RequestApplied[V, D] protected[TransactionalClient] (
    operation:           GraphQLOperation[S],
    operationName:       Option[String]
  )(implicit varEncoder: Encoder[V], dataDecoder: Decoder[D]) {
    def apply(variables: V): F[D] =
      requestInternal[D](operation.document, operationName, variables.asJson.some)

    def apply: F[D] =
      requestInternal(operation.document, operationName, none)
  }

  object RequestApplied {
    implicit def withoutVariables[V, D](applied: RequestApplied[V, D]): F[D] = applied.apply
  }

  def request(
    operation:     GraphQLOperation[S],
    operationName: Option[String] = None
  ): RequestApplied[operation.Variables, operation.Data] = {
    import operation.implicits._
    RequestApplied(operation, operationName)
  }

  protected def requestInternal[D: Decoder](
    document:      String,
    operationName: Option[String] = None,
    variables:     Option[Json] = None
  ): F[D]
}

object TransactionalClient {
  def of[F[_], S](uri: Uri, name: String = "", headers: Headers = Headers.empty)(implicit
    F:                 MonadError[F, Throwable],
    backend:           TransactionalBackend[F],
    logger:            Logger[F]
  ): F[TransactionalClient[F, S]] = {
    val logPrefix = s"clue.TransactionalClient[${if (name.isEmpty) uri else name}]"

    Applicative[F].pure(
      new TransactionalClientImpl[F, S](uri, headers)(
        F,
        backend,
        logger.withModifiedString(s => s"$logPrefix $s")
      )
    )
  }
}

/**
 * A client that allows subscriptions in addition to one-shot queries and mutations.
 */
trait StreamingClient[F[_], S] extends TransactionalClient[F, S] {

  case class SubscriptionApplied[V, D] protected[StreamingClient] (
    subscription:        GraphQLOperation[S],
    operationName:       Option[String] = None
  )(implicit varEncoder: Encoder[V], dataDecoder: Decoder[D]) {
    def apply(variables: V): Resource[F, fs2.Stream[F, D]] =
      subscribeInternal[D](subscription.document, operationName, variables.asJson.some)

    def apply: Resource[F, fs2.Stream[F, D]] =
      subscribeInternal[D](subscription.document, operationName)
  }
  object SubscriptionApplied {
    implicit def withoutVariables[V, D](
      applied: SubscriptionApplied[V, D]
    ): Resource[F, fs2.Stream[F, D]] =
      applied.apply
  }

  def subscribe(
    subscription:  GraphQLOperation[S],
    operationName: Option[String] = None
  ): SubscriptionApplied[subscription.Variables, subscription.Data] = {
    import subscription.implicits._
    SubscriptionApplied(subscription, operationName)
  }

  protected def subscribeInternal[D: Decoder](
    document:      String,
    operationName: Option[String] = None,
    variables:     Option[Json] = None
  ): Resource[F, fs2.Stream[F, D]]
}

/**
 * A client that keeps a connection open and initializable protocol with the server.
 */
trait PersistentClient[F[_], CP, CE] {
  // protected val backend: PersistentBackend[F, CP, CE]
  // protected val reconnectionStrategy: ReconnectionStrategy[CE]

  def status: F[PersistentClientStatus]
  def statusStream: fs2.Stream[F, PersistentClientStatus]

  def connect(): F[Unit]
  def initialize(payload: Map[String, Json] = Map.empty): F[Unit]

  def terminate(): F[Unit]
  def disconnect(closeParameters: CP): F[Unit]
  def disconnect(): F[Unit]

  def reestablish(): F[Unit]
}

/**
 * A client that keeps a connection open and initializable protocol with the server, and allows
 * GraphQL queries, mutations and subscriptions.
 */
trait PersistentStreamingClient[F[_], S, CP, CE]
    extends StreamingClient[F, S]
    with PersistentClient[F, CP, CE]
