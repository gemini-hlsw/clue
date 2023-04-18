// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.effect.Resource
import cats.syntax.all._
import clue.ErrorPolicy
import io.circe._
import io.circe.syntax._

/**
 * A client that allows one-shot queries and mutations.
 */
trait FetchClientWithPars[F[_], P, S] {
  case class RequestApplied[V: Encoder.AsObject, D: Decoder, R] protected[FetchClientWithPars] (
    operation:     GraphQLOperation[S],
    operationName: Option[String],
    errorPolicy:   ErrorPolicyProcessor[D, R]
  ) {
    def withInput(variables: V): F[R] =
      withInput(variables, identity)

    def withInput(variables: V, modParams: P => P): F[R] =
      requestInternal(
        operation.document,
        operationName,
        variables.asJsonObject.some,
        modParams,
        errorPolicy
      )

    def withModParams(modParams: P => P): F[R] =
      requestInternal(operation.document, operationName, none, modParams, errorPolicy)

    def apply: F[R] =
      requestInternal(operation.document, operationName, none, identity, errorPolicy)
  }

  object RequestApplied {
    implicit def withoutVariables[V, D, R](
      applied: RequestApplied[V, D, R]
    ): F[R] = applied.apply
  }

  def request(
    operation:     GraphQLOperation[S],
    operationName: Option[String] = none
  )(implicit
    errorPolicy:   ErrorPolicy
  ): RequestApplied[
    operation.Variables,
    operation.Data,
    errorPolicy.ReturnType[operation.Data]
  ] = {
    import operation.implicits._
    RequestApplied(operation, operationName, errorPolicy.processor[operation.Data])
  }

  protected def requestInternal[D: Decoder, R](
    document:      String,
    operationName: Option[String] = none,
    variables:     Option[JsonObject] = none,
    modParams:     P => P = identity,
    errorPolicy:   ErrorPolicyProcessor[D, R]
  ): F[R]
}

/**
 * A client that allows subscriptions in addition to one-shot queries and mutations.
 */
trait StreamingClient[F[_], S] extends FetchClientWithPars[F, Unit, S] {
  case class SubscriptionApplied[V: Encoder.AsObject, D: Decoder, R] protected[StreamingClient] (
    subscription:  GraphQLOperation[S],
    operationName: Option[String] = none,
    errorPolicy:   ErrorPolicyProcessor[D, R]
  ) {
    def withInput(variables: V): Resource[F, fs2.Stream[F, R]] =
      subscribeInternal(
        subscription.document,
        operationName,
        variables.asJsonObject.some,
        errorPolicy
      )

    def apply: Resource[F, fs2.Stream[F, R]] =
      subscribeInternal(subscription.document, operationName, none, errorPolicy)
  }
  object SubscriptionApplied {
    implicit def withoutVariables[V, D, R](
      applied: SubscriptionApplied[V, D, R]
    ): Resource[F, fs2.Stream[F, R]] =
      applied.apply
  }

  def subscribe(
    subscription:  GraphQLOperation[S],
    operationName: Option[String] = none
  )(implicit
    errorPolicy:   ErrorPolicy
  ): SubscriptionApplied[
    subscription.Variables,
    subscription.Data,
    errorPolicy.ReturnType[subscription.Data]
  ] = {
    import subscription.implicits._
    SubscriptionApplied(subscription, operationName, errorPolicy.processor[subscription.Data])
  }

  protected def subscribeInternal[D: Decoder, R](
    document:      String,
    operationName: Option[String] = none,
    variables:     Option[JsonObject] = none,
    errorPolicy:   ErrorPolicyProcessor[D, R]
  ): Resource[F, fs2.Stream[F, R]]
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
