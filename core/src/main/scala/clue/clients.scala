// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.Applicative
import cats.MonadThrow
import cats.effect.Resource
import cats.effect.Sync
import cats.syntax.all.*
import clue.model.GraphQLQuery
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
    import operation.givens.given
    RequestApplied(this, operation, operationName)
  }

  protected[clue] def requestInternal[D: Decoder](
    document:      GraphQLQuery,
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
      GraphQLQuery(operation.document),
      operationName,
      variables.asJsonObject.some,
      modParams
    )

  def withModParams(modParams: P => P): F[GraphQLResponse[D]] =
    client.requestInternal(GraphQLQuery(operation.document), operationName, none, modParams)

  def apply: F[GraphQLResponse[D]] =
    client.requestInternal(GraphQLQuery(operation.document), operationName, none, identity)
}

object RequestApplied {
  given [F[_], P, S, V, D]: Conversion[RequestApplied[F, P, S, V, D], F[GraphQLResponse[D]]] =
    _.apply

  extension [F[_], P, S, V, D](applied: RequestApplied[F, P, S, V, D]) {
    def raiseGraphQLErrors(using F: MonadThrow[F]): F[D] =
      clue.syntax.raiseGraphQLErrors(applied.apply)

    def raiseGraphQLErrorsOnNoData(using MonadThrow[F]): F[D] =
      clue.syntax.raiseGraphQLErrorsOnNoData(applied.apply)
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
    import subscription.givens.given
    SubscriptionApplied(this, subscription, operationName)
  }

  protected[clue] def subscribeInternal[D: Decoder](
    document:      GraphQLQuery,
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
      GraphQLQuery(subscription.document),
      operationName,
      variables.asJsonObject.some
    )

  def apply: Resource[F, fs2.Stream[F, GraphQLResponse[D]]] =
    client.subscribeInternal(GraphQLQuery(subscription.document), operationName, none)
}

object SubscriptionApplied {
  given [F[_], S, V, D]
    : Conversion[SubscriptionApplied[F, S, V, D], Resource[F, fs2.Stream[F, GraphQLResponse[D]]]] =
    _.apply

  extension [F[_], S, V, D](applied: SubscriptionApplied[F, S, V, D]) {
    def ignoreGraphQLErrors: Resource[F, fs2.Stream[F, D]] =
      clue.syntax.ignoreGraphQLErrors(applied.apply)

    def raiseFirstNoDataError(using Sync[F]): Resource[F, fs2.Stream[F, GraphQLResponse[D]]] =
      clue.syntax.raiseFirstNoDataError(applied.apply)

    def handleGraphQLErrors(
      onError: ResponseException[D] => F[Unit]
    )(using Applicative[F]): Resource[F, fs2.Stream[F, D]] =
      clue.syntax.handleGraphQLErrors(applied.apply)(onError)

    def logGraphQLErrors(
      msg: ResponseException[D] => String
    )(using Applicative[F], Logger[F]): Resource[F, fs2.Stream[F, D]] =
      clue.syntax.logGraphQLErrors(applied.apply)(msg)
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
