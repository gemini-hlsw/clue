// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model

import cats.Applicative
import cats.Eq
import cats.Eval
import cats.MonadThrow
import cats.Traverse
import cats.data.Ior
import cats.effect.Ref
import cats.effect.Resource
import cats.effect.Sync
import cats.syntax.all.*
import clue.ResponseException
import org.typelevel.log4cats.Logger

/**
 * A GraphQL response.
 *
 * See https://spec.graphql.org/October2021/#sec-Response-Format
 *
 * @param result
 *   request result with possible data and errors raised by the request
 * @param extensions
 *   values for protocol extension
 */
final case class GraphQLResponse[D](
  result:     Ior[GraphQLErrors, D],
  extensions: Option[GraphQLExtensions] = none
) {
  final lazy val data: Option[D]               = result.right
  final lazy val errors: Option[GraphQLErrors] = result.left

  final def traverse[F[_], E](
    g: D => F[E]
  )(implicit F: Applicative[F]): F[GraphQLResponse[E]] =
    this.result match {
      case Ior.Left(a)    => F.pure(GraphQLResponse(Ior.left(a), extensions))
      case Ior.Right(b)   => F.map(g(b))(e => GraphQLResponse(Ior.right(e), extensions))
      case Ior.Both(a, b) => F.map(g(b))(e => GraphQLResponse(Ior.both(a, e), extensions))
    }

  final def foldLeft[E](e: E)(f: (E, D) => E): E =
    result.foldLeft(e)(f)

  final def foldRight[E](lc: Eval[E])(f: (D, Eval[E]) => Eval[E]): Eval[E] =
    result.foldRight(lc)(f)
}

object GraphQLResponse {
  final def errors[D](e: GraphQLErrors): GraphQLResponse[D] =
    GraphQLResponse(Ior.left(e))

  final implicit def eqGraphQLResponse[D: Eq]: Eq[GraphQLResponse[D]] =
    Eq.by(x => (x.result, x.extensions))

  final implicit val traverseGraphQLResponse: Traverse[GraphQLResponse] =
    new Traverse[GraphQLResponse] {
      def traverse[G[_], A, B](fa: GraphQLResponse[A])(f: A => G[B])(implicit
        G: Applicative[G]
      ): G[GraphQLResponse[B]] =
        fa.traverse(f)

      def foldLeft[D, E](fa: GraphQLResponse[D], e: E)(f: (E, D) => E): E =
        fa.foldLeft(e)(f)

      def foldRight[D, E](fa: GraphQLResponse[D], lb: cats.Eval[E])(
        f: (D, cats.Eval[E]) => cats.Eval[E]
      ): cats.Eval[E] =
        fa.foldRight(lb)(f)
    }

  final implicit class GraphQLResponseOps[F[_], D](
    val response: F[GraphQLResponse[D]]
  ) extends AnyVal {
    // If you add methods here, be sure to add them too to the proxies in
    // clue.syntax and clients.RequestApplied
    def raiseGraphQLErrors(implicit F: MonadThrow[F]): F[D] =
      response.map(_.result).flatMap {
        case Ior.Right(b)   => F.pure(b)
        case Ior.Both(e, b) => F.raiseError(ResponseException(e, b.some))
        case Ior.Left(e)    => F.raiseError(ResponseException(e, none))
      }

    def raiseGraphQLErrorsOnNoData(implicit F: MonadThrow[F]): F[D] =
      response.map(_.result).flatMap {
        case Ior.Right(b)   => F.pure(b)
        case Ior.Both(_, b) => F.pure(b)
        case Ior.Left(e)    => F.raiseError(ResponseException(e, none))
      }
  }

  final implicit class GraphQLResponseResourceStreamOps[F[_], D](
    val streamResource: Resource[F, fs2.Stream[F, GraphQLResponse[D]]]
  ) extends AnyVal {
    // If you add methods here, be sure to add them too to the proxies in
    // clue.syntax and clients.SubscriptionApplied
    def ignoreGraphQLErrors: Resource[F, fs2.Stream[F, D]] =
      streamResource.map(
        _.through(
          _.map(_.result).collect {
            case Ior.Right(b)   => b
            case Ior.Both(_, b) => b
          }
        )
      )

    /**
     * Raise the first error found in the stream, which most probably indicates an error with the
     * subscription.
     */
    def raiseFirstNoDataError(implicit F: Sync[F]): Resource[F, fs2.Stream[F, GraphQLResponse[D]]] =
      streamResource.map { stream =>
        for {
          ref       <- fs2.Stream.eval(Ref.of[F, Boolean](false)) // Data received?
          refStream  = fs2.Stream.eval(ref.get).repeat
          newStream <- stream.zip(refStream).flatMap {
                         case (GraphQLResponse(Ior.Left(e), _), false) =>
                           fs2.Stream.raiseError(ResponseException(e, none))
                         case (r, false)                               =>
                           fs2.Stream.eval(ref.set(true).as(r))
                         case (r, _)                                   =>
                           fs2.Stream.emit(r)
                       }
        } yield newStream
      }

    def handleGraphQLErrors(
      onError: ResponseException[D] => F[Unit]
    )(implicit F: Applicative[F]): Resource[F, fs2.Stream[F, D]] =
      streamResource.map(
        _.through(
          _.evalTap(_.result match {
            case Ior.Left(a)    => onError(ResponseException(a, none))
            case Ior.Right(b)   => F.unit
            case Ior.Both(a, b) => onError(ResponseException(a, b.some))
          })
            .map(_.data)
            .flattenOption
        )
      )

    def logGraphQLErrors(msg: ResponseException[D] => String)(implicit
      F:      Applicative[F],
      logger: Logger[F]
    ): Resource[F, fs2.Stream[F, D]] =
      handleGraphQLErrors(e => logger.error(e)(msg(e)))
  }
}
