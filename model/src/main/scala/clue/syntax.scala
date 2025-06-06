// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.Applicative
import cats.MonadThrow
import cats.effect.Resource
import cats.effect.Sync
import clue.model.GraphQLResponse
import org.typelevel.log4cats.Logger

// When F is a concrete type importing this is not necessary since the
// compiler will pick up the extensions in GraphQLResponse. However,
// if it's invoked with a generic F, the compiler won't pick those up
// and importing this is necessary.
object syntax {
  final implicit class GraphQLResponseOps[F[_], D](
    val response: F[GraphQLResponse[D]]
  ) extends AnyVal {
    def raiseGraphQLErrors(implicit F: MonadThrow[F]): F[D] =
      new GraphQLResponse.GraphQLResponseOps(response).raiseGraphQLErrors

    def raiseGraphQLErrorsOnNoData(implicit F: MonadThrow[F]): F[D] =
      new GraphQLResponse.GraphQLResponseOps(response).raiseGraphQLErrorsOnNoData
  }

  final implicit class GraphQLResponseResourceStreamOps[F[_], D](
    val streamResource: Resource[F, fs2.Stream[F, GraphQLResponse[D]]]
  ) extends AnyVal {
    def ignoreGraphQLErrors: Resource[F, fs2.Stream[F, D]] =
      new GraphQLResponse.GraphQLResponseResourceStreamOps(streamResource).ignoreGraphQLErrors

    def raiseFirstNoDataError(implicit F: Sync[F]): Resource[F, fs2.Stream[F, GraphQLResponse[D]]] =
      new GraphQLResponse.GraphQLResponseResourceStreamOps(streamResource).raiseFirstNoDataError

    def handleGraphQLErrors(
      onError: ResponseException[D] => F[Unit]
    )(implicit F: Applicative[F]): Resource[F, fs2.Stream[F, D]] =
      new GraphQLResponse.GraphQLResponseResourceStreamOps(streamResource)
        .handleGraphQLErrors(onError)

    def logGraphQLErrors(msg: ResponseException[D] => String)(implicit
      F:      Applicative[F],
      logger: Logger[F]
    ): Resource[F, fs2.Stream[F, D]] =
      new GraphQLResponse.GraphQLResponseResourceStreamOps(streamResource).logGraphQLErrors(msg)
  }
}
