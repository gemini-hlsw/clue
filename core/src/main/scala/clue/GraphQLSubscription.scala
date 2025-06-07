// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

/*
 * Internal class to manage subscriptions.
 */
protected trait GraphQLSubscription[F[_], D] {
  // Subscription data stream.
  val stream: fs2.Stream[F, D]

  // Can be called by the client to stop the subscription.
  def stop(): F[Unit]
}
