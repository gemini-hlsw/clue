// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.syntax.all._
import io.circe._
import io.circe.syntax._

trait GraphQLStreamingClient[F[_]] extends GraphQLClient[F] {
  def status: F[StreamingClientStatus]

  def statusStream: fs2.Stream[F, StreamingClientStatus]

  def close(): F[Unit]

  protected trait StoppableSubscription[D] {
    val stream: fs2.Stream[F, D]
    def stop(): F[Unit]
  }

  type Subscription[D] <: StoppableSubscription[D]

  def subscribe(
    graphQLQuery: GraphQLQuery
  )(variables:    Option[graphQLQuery.Variables] = None): F[Subscription[graphQLQuery.Data]] = {
    import graphQLQuery.implicits._

    variables.fold(subscribe[graphQLQuery.Data](graphQLQuery.document)) { v =>
      subscribe[graphQLQuery.Variables, graphQLQuery.Data](graphQLQuery.document, v)
    }
  }

  def subscribe[V: Encoder, D: Decoder](
    subscription:  String,
    variables:     V,
    operationName: String
  ): F[Subscription[D]] =
    subscribeInternal[D](subscription, operationName.some, variables.asJson.some)

  def subscribe[D: Decoder](
    subscription:  String,
    operationName: String
  ): F[Subscription[D]] =
    subscribeInternal[D](subscription, operationName.some)

  def subscribe[V: Encoder, D: Decoder](
    subscription: String,
    variables:    V
  ): F[Subscription[D]] =
    subscribeInternal[D](subscription, None, variables.asJson.some)

  def subscribe[D: Decoder](subscription: String): F[Subscription[D]] =
    subscribeInternal[D](subscription)

  protected def subscribeInternal[D: Decoder](
    subscription:  String,
    operationName: Option[String] = None,
    variables:     Option[Json] = None
  ): F[Subscription[D]]
}
