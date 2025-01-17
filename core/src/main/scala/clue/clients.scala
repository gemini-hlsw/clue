// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.Applicative
import cats.MonadThrow
import cats.effect.Resource
import cats.effect.Sync
import cats.syntax.all.*
import clue.model.GraphQLResponse
import io.circe.*
import io.circe.syntax.*
import org.typelevel.log4cats.Logger

/**
 * A client that allows one-shot queries and mutations.
 */
trait FetchClientWithPars[F[_], P, S] {
  def request(
    operation:     GraphQLOperation[S],
    operationName: Option[String] = none
  ): RequestApplied[F, P, S, operation.Variables, operation.Data] = {
    import operation.implicits._
    RequestApplied(this, operation, operationName)
  }

  protected[clue] def requestInternal[D: Decoder](
    document:      String,
    operationName: Option[String] = none,
    variables:     Option[JsonObject] = none,
    modParams:     P => P = identity
  ): F[GraphQLResponse[D]]
}

case class RequestApplied[
  F[_],
  P,
  S,
  V: Encoder.AsObject,
  D: Decoder
] protected[clue] (
  client:        FetchClientWithPars[F, P, S],
  operation:     GraphQLOperation[S],
  operationName: Option[String]
) {
  def withInput(variables: V): F[GraphQLResponse[D]] =
    withInput(variables, identity)

  def withInput(variables: V, modParams: P => P): F[GraphQLResponse[D]] =
    client.requestInternal(
      operation.document,
      operationName,
      variables.asJsonObject.some,
      modParams
    )

  def withModParams(modParams: P => P): F[GraphQLResponse[D]] =
    client.requestInternal(operation.document, operationName, none, modParams)

  def apply: F[GraphQLResponse[D]] =
    client.requestInternal(operation.document, operationName, none, identity)
}

object RequestApplied {
  implicit def withoutVariables[F[_], P, S, V, D](
    applied: RequestApplied[F, P, S, V, D]
  ): F[GraphQLResponse[D]] = applied.apply

  final implicit class RequestAppliedOps[F[_], P, S, V, D](
    val applied: RequestApplied[F, P, S, V, D]
  ) extends AnyVal {
    def raiseGraphQLErrors(implicit F: MonadThrow[F]): F[D] =
      new GraphQLResponse.GraphQLResponseOps(applied.apply).raiseGraphQLErrors

    def raiseGraphQLErrorsOnNoData(implicit F: MonadThrow[F]): F[D] =
      new GraphQLResponse.GraphQLResponseOps(applied.apply).raiseGraphQLErrorsOnNoData
  }
}

/**
 * A client that allows subscriptions in addition to one-shot queries and mutations.
 */
trait StreamingClient[F[_], S] extends FetchClientWithPars[F, Unit, S] {
  def subscribe(
    subscription:  GraphQLOperation[S],
    operationName: Option[String] = none
  ): SubscriptionApplied[F, S, subscription.Variables, subscription.Data] = {
    import subscription.implicits._
    SubscriptionApplied(this, subscription, operationName)
  }

  protected[clue] def subscribeInternal[D: Decoder](
    document:      String,
    operationName: Option[String] = none,
    variables:     Option[JsonObject] = none
  ): Resource[F, fs2.Stream[F, GraphQLResponse[D]]]
}

case class SubscriptionApplied[
  F[_],
  S,
  V: Encoder.AsObject,
  D: Decoder
] protected[clue] (
  client:        StreamingClient[F, S],
  subscription:  GraphQLOperation[S],
  operationName: Option[String] = none
) {
  def withInput(variables: V): Resource[F, fs2.Stream[F, GraphQLResponse[D]]] =
    client.subscribeInternal(
      subscription.document,
      operationName,
      variables.asJsonObject.some
    )

  def apply: Resource[F, fs2.Stream[F, GraphQLResponse[D]]] =
    client.subscribeInternal(subscription.document, operationName, none)
}

object SubscriptionApplied {
  implicit def withoutVariables[F[_], S, V, D](
    applied: SubscriptionApplied[F, S, V, D]
  ): Resource[F, fs2.Stream[F, GraphQLResponse[D]]] =
    applied.apply

  final implicit class SubscriptionAppliedOps[F[_], S, V, D](
    val applied: SubscriptionApplied[F, S, V, D]
  ) extends AnyVal {
    def ignoreGraphQLErrors: Resource[F, fs2.Stream[F, D]] =
      new GraphQLResponse.GraphQLResponseResourceStreamOps(applied.apply).ignoreGraphQLErrors

    def raiseFirstNoDataError(implicit F: Sync[F]): Resource[F, fs2.Stream[F, GraphQLResponse[D]]] =
      new GraphQLResponse.GraphQLResponseResourceStreamOps(applied.apply).raiseFirstNoDataError

    def handleGraphQLErrors(
      onError: ResponseException[D] => F[Unit]
    )(implicit F: Applicative[F]): Resource[F, fs2.Stream[F, D]] =
      new GraphQLResponse.GraphQLResponseResourceStreamOps(applied.apply)
        .handleGraphQLErrors(onError)

    def logGraphQLErrors(msg: ResponseException[D] => String)(implicit
      F:      Applicative[F],
      logger: Logger[F]
    ): Resource[F, fs2.Stream[F, D]] =
      new GraphQLResponse.GraphQLResponseResourceStreamOps(applied.apply).logGraphQLErrors(msg)
  }
}

/**
 * A client that keeps a connection open and initializable protocol with the server.
 */
trait PersistentClient[F[_], CP, CE] {
  def status: F[PersistentClientStatus]
  def statusStream: fs2.Stream[F, PersistentClientStatus]

  // Initialization may repeat upon reconnection, that's why the payload is effectful since it may change over time (eg: auth tokens).
  def connect(payload: F[Map[String, Json]]): F[Unit]
  def connect(): F[Unit]

  def disconnect(closeParameters: CP): F[Unit]
  def disconnect(): F[Unit]
}

/**
 * A client that keeps a connection open and initializable protocol with the server, and allows
 * GraphQL queries, mutations and subscriptions.
 */
trait PersistentStreamingClient[F[_], S, CP, CE]
    extends StreamingClient[F, S]
    with PersistentClient[F, CP, CE]
