// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.syntax.all._
import io.circe._
import io.circe.syntax._
import org.typelevel.log4cats.Logger
import sttp.model.Uri
import cats.MonadError
import cats.Applicative

/**
 * A client that allows one-shot queries and mutations.
 */
trait TransactionalClient[F[_], S] {
  def request(
    operation:     GraphQLOperation[S],
    operationName: Option[String] = None
  ): RequestApplied[operation.Variables, operation.Data] = {
    import operation.implicits._
    RequestApplied(operation, operationName)
  }

  case class RequestApplied[V, D] private (
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

  protected def requestInternal[D: Decoder](
    document:      String,
    operationName: Option[String] = None,
    variables:     Option[Json] = None
  ): F[D]
}

object TransactionalClient {
  def of[F[_], S](uri: Uri, name: String = "")(implicit
    F:                 MonadError[F, Throwable],
    backend:           TransactionalBackend[F],
    logger:            Logger[F]
  ): F[TransactionalClient[F, S]] = {
    val logPrefix = s"clue.TransactionalClient[${if (name.isEmpty) uri else name}]"

    Applicative[F].pure(
      new TransactionalClientImpl[F, S](uri)(
        F,
        backend,
        logger.withModifiedString(s => s"$logPrefix $s")
      )
    )
  }
}

/*
 * A subscription, as seen from the client.
 */
trait GraphQLSubscription[F[_], D] {
  // Subscription data stream.
  val stream: fs2.Stream[F, D]

  // Can be called by the client to stop the subscription.
  def stop(): F[Unit]
}

/**
 * A client that allows subscriptions in addition to one-shot queries and mutations.
 */
trait StreamingClient[F[_], S] extends TransactionalClient[F, S] {
  def subscribe(
    subscription:  GraphQLOperation[S],
    operationName: Option[String] = None
  ): SubscriptionApplied[subscription.Variables, subscription.Data] = {
    import subscription.implicits._
    SubscriptionApplied(subscription, operationName)
  }

  case class SubscriptionApplied[V, D] private (
    subscription:        GraphQLOperation[S],
    operationName:       Option[String] = None
  )(implicit varEncoder: Encoder[V], dataDecoder: Decoder[D]) {
    def apply(variables: V): F[GraphQLSubscription[F, D]] =
      subscribeInternal[D](subscription.document, operationName, variables.asJson.some)

    def apply: F[GraphQLSubscription[F, D]] =
      subscribeInternal[D](subscription.document, operationName)
  }
  object SubscriptionApplied {
    implicit def withoutVariables[V, D](
      applied: SubscriptionApplied[V, D]
    ): F[GraphQLSubscription[F, D]] =
      applied.apply
  }

  protected def subscribeInternal[D: Decoder](
    document:      String,
    operationName: Option[String] = None,
    variables:     Option[Json] = None
  ): F[GraphQLSubscription[F, D]]
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
 * A client that keeps a connection open and initializable protocol with the server,
 * and allows GraphQL queries, mutations and subscriptions.
 */
trait PersistentStreamingClient[F[_], S, CP, CE]
    extends StreamingClient[F, S]
    with PersistentClient[F, CP, CE]
