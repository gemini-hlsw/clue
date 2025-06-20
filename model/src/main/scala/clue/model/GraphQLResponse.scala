// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model

import cats.Applicative
import cats.Eq
import cats.Eval
import cats.MonadThrow
import cats.Traverse
import cats.data.Ior
import cats.effect.Resource
import cats.effect.Sync
import cats.syntax.all.*
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
):
  final lazy val data: Option[D]               = result.right
  final lazy val errors: Option[GraphQLErrors] = result.left

  final def traverse[F[_], E](
    g: D => F[E]
  )(using F: Applicative[F]): F[GraphQLResponse[E]] =
    this.result match
      case Ior.Left(a)    => F.pure(GraphQLResponse(Ior.left(a), extensions))
      case Ior.Right(b)   => F.map(g(b))(e => GraphQLResponse(Ior.right(e), extensions))
      case Ior.Both(a, b) => F.map(g(b))(e => GraphQLResponse(Ior.both(a, e), extensions))

  final def foldLeft[E](e: E)(f: (E, D) => E): E =
    result.foldLeft(e)(f)

  final def foldRight[E](lc: Eval[E])(f: (D, Eval[E]) => Eval[E]): Eval[E] =
    result.foldRight(lc)(f)

object GraphQLResponse:
  final def errors[D](e: GraphQLErrors): GraphQLResponse[D] =
    GraphQLResponse(Ior.left(e))

  given [D: Eq]: Eq[GraphQLResponse[D]] =
    Eq.by(x => (x.result, x.extensions))

  given Traverse[GraphQLResponse] =
    new Traverse[GraphQLResponse]:
      def traverse[G[_]: Applicative, A, B](fa: GraphQLResponse[A])(
        f: A => G[B]
      ): G[GraphQLResponse[B]] =
        fa.traverse(f)

      def foldLeft[D, E](fa: GraphQLResponse[D], e: E)(f: (E, D) => E): E =
        fa.foldLeft(e)(f)

      def foldRight[D, E](fa: GraphQLResponse[D], lb: cats.Eval[E])(
        f: (D, cats.Eval[E]) => cats.Eval[E]
      ): cats.Eval[E] =
        fa.foldRight(lb)(f)

  // Enables extensions without importing clue.syntax.
  export clue.syntax.*
