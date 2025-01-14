// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.effect.Resource
import cats.syntax.all.*
import clue.model.GraphQLResponse
import io.circe.*
import io.circe.syntax.*

/**
 * A client that allows one-shot queries and mutations.
 */
trait FetchClientWithPars[F[_], P, S] {
  case class RequestApplied[V: Encoder.AsObject, D: Decoder] protected[FetchClientWithPars] (
    operation:     GraphQLOperation[S],
    operationName: Option[String]
  ) {
    def withInput(variables: V): F[GraphQLResponse[D]] =
      withInput(variables, identity)

    def withInput(variables: V, modParams: P => P): F[GraphQLResponse[D]] =
      requestInternal(
        operation.document,
        operationName,
        variables.asJsonObject.some,
        modParams
      )

    def withModParams(modParams: P => P): F[GraphQLResponse[D]] =
      requestInternal(operation.document, operationName, none, modParams)

    def apply: F[GraphQLResponse[D]] =
      requestInternal(operation.document, operationName, none, identity)
  }

  object RequestApplied {
    implicit def withoutVariables[V, D](
      applied: RequestApplied[V, D]
    ): F[GraphQLResponse[D]] = applied.apply
  }

  def request(
    operation:     GraphQLOperation[S],
    operationName: Option[String] = none
  ): RequestApplied[operation.Variables, operation.Data] = {
    import operation.implicits._
    RequestApplied(operation, operationName)
  }

  protected def requestInternal[D: Decoder](
    document:      String,
    operationName: Option[String] = none,
    variables:     Option[JsonObject] = none,
    modParams:     P => P = identity
  ): F[GraphQLResponse[D]]
}

/**
 * A client that allows subscriptions in addition to one-shot queries and mutations.
 */
trait StreamingClient[F[_], S] extends FetchClientWithPars[F, Unit, S] {
  case class SubscriptionApplied[V: Encoder.AsObject, D: Decoder] protected[StreamingClient] (
    subscription:  GraphQLOperation[S],
    operationName: Option[String] = none
  ) {
    def withInput(variables: V): Resource[F, fs2.Stream[F, GraphQLResponse[D]]] =
      subscribeInternal(
        subscription.document,
        operationName,
        variables.asJsonObject.some
      )

    def apply: Resource[F, fs2.Stream[F, GraphQLResponse[D]]] =
      subscribeInternal(subscription.document, operationName, none)
  }
  object SubscriptionApplied {
    implicit def withoutVariables[V, D](
      applied: SubscriptionApplied[V, D]
    ): Resource[F, fs2.Stream[F, GraphQLResponse[D]]] =
      applied.apply
  }

  def subscribe(
    subscription:  GraphQLOperation[S],
    operationName: Option[String] = none
  ): SubscriptionApplied[subscription.Variables, subscription.Data] = {
    import subscription.implicits._
    SubscriptionApplied(subscription, operationName)
  }

  protected def subscribeInternal[D: Decoder](
    document:      String,
    operationName: Option[String] = none,
    variables:     Option[JsonObject] = none
  ): Resource[F, fs2.Stream[F, GraphQLResponse[D]]]
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
