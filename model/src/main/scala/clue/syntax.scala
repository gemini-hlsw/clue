// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.Applicative
import cats.MonadThrow
import cats.data.Ior
import cats.effect.Ref
import cats.effect.Resource
import cats.effect.Sync
import cats.syntax.all.*
import clue.model.GraphQLResponse
import org.typelevel.log4cats.Logger

object syntax:
  extension [F[_], D](response: F[GraphQLResponse[D]])
    // If you add methods here, be sure to add them too to the proxies in
    // clue.syntax and clients.RequestApplied
    def raiseGraphQLErrors(using F: MonadThrow[F]): F[D] =
      response
        .map(_.result)
        .flatMap:
          case Ior.Right(b)   => F.pure(b)
          case Ior.Both(e, b) => F.raiseError(ResponseException(e, b.some))
          case Ior.Left(e)    => F.raiseError(ResponseException(e, none))

    def raiseGraphQLErrorsOnNoData(using F: MonadThrow[F]): F[D] =
      response
        .map(_.result)
        .flatMap:
          case Ior.Right(b)   => F.pure(b)
          case Ior.Both(_, b) => F.pure(b)
          case Ior.Left(e)    => F.raiseError(ResponseException(e, none))

  extension [F[_], D](streamResource: Resource[F, fs2.Stream[F, GraphQLResponse[D]]])
    // If you add methods here, be sure to add them too to the proxies in
    // clue.syntax and clients.SubscriptionApplied
    def ignoreGraphQLErrors: Resource[F, fs2.Stream[F, D]] =
      streamResource.map:
        _.through:
          _.map(_.result).collect:
            case Ior.Right(b)   => b
            case Ior.Both(_, b) => b

    /**
     * Raise the first error found in the stream, which most probably indicates an error with the
     * subscription.
     */
    def raiseFirstNoDataError(using F: Sync[F]): Resource[F, fs2.Stream[F, GraphQLResponse[D]]] =
      streamResource.map: stream =>
        for
          ref       <- fs2.Stream.eval(Ref.of[F, Boolean](false)) // Data received?
          refStream  = fs2.Stream.eval(ref.get).repeat
          newStream <-
            stream
              .zip(refStream)
              .flatMap:
                case (GraphQLResponse(Ior.Left(e), _), false) =>
                  fs2.Stream.raiseError(ResponseException(e, none))
                case (r, false)                               =>
                  fs2.Stream.eval(ref.set(true).as(r))
                case (r, _)                                   =>
                  fs2.Stream.emit(r)
        yield newStream

    def handleGraphQLErrors(
      onError: ResponseException[D] => F[Unit]
    )(using F: Applicative[F]): Resource[F, fs2.Stream[F, D]] =
      streamResource.map:
        _.through:
          _.evalTap:
            _.result match
              case Ior.Left(a)    => onError(ResponseException(a, none))
              case Ior.Right(b)   => F.unit
              case Ior.Both(a, b) => onError(ResponseException(a, b.some))
          .map(_.data)
            .flattenOption

    def logGraphQLErrors(msg: ResponseException[D] => String)(implicit
      F:      Applicative[F],
      logger: Logger[F]
    ): Resource[F, fs2.Stream[F, D]] =
      handleGraphQLErrors(e => logger.error(e)(msg(e)))

// // When F is a concrete type importing this is not necessary since the
// // compiler will pick up the extensions in GraphQLResponse. However,
// // if it's invoked with a generic F, the compiler won't pick those up
// // and importing this is necessary.
// object syntax {
//   final implicit class GraphQLResponseOps[F[_], D](
//     val response: F[GraphQLResponse[D]]
//   ) extends AnyVal {
//     def raiseGraphQLErrors(implicit F: MonadThrow[F]): F[D] =
//       new GraphQLResponse.GraphQLResponseOps(response).raiseGraphQLErrors

//     def raiseGraphQLErrorsOnNoData(implicit F: MonadThrow[F]): F[D] =
//       new GraphQLResponse.GraphQLResponseOps(response).raiseGraphQLErrorsOnNoData
//   }

//   final implicit class GraphQLResponseResourceStreamOps[F[_], D](
//     val streamResource: Resource[F, fs2.Stream[F, GraphQLResponse[D]]]
//   ) extends AnyVal {
//     def ignoreGraphQLErrors: Resource[F, fs2.Stream[F, D]] =
//       new GraphQLResponse.GraphQLResponseResourceStreamOps(streamResource).ignoreGraphQLErrors

//     def raiseFirstNoDataError(implicit F: Sync[F]): Resource[F, fs2.Stream[F, GraphQLResponse[D]]] =
//       new GraphQLResponse.GraphQLResponseResourceStreamOps(streamResource).raiseFirstNoDataError

//     def handleGraphQLErrors(
//       onError: ResponseException[D] => F[Unit]
//     )(implicit F: Applicative[F]): Resource[F, fs2.Stream[F, D]] =
//       new GraphQLResponse.GraphQLResponseResourceStreamOps(streamResource)
//         .handleGraphQLErrors(onError)

//     def logGraphQLErrors(msg: ResponseException[D] => String)(implicit
//       F:      Applicative[F],
//       logger: Logger[F]
//     ): Resource[F, fs2.Stream[F, D]] =
//       new GraphQLResponse.GraphQLResponseResourceStreamOps(streamResource).logGraphQLErrors(msg)
//   }
// }
