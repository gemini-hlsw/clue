// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model

import cats.Eq
import cats.data.Ior
import cats.syntax.all.*
import cats.Applicative
import cats.Traverse
import cats.Eval

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
  def errors[D](e: GraphQLErrors): GraphQLResponse[D] =
    GraphQLResponse(Ior.left(e))

  implicit def eqGraphQLResponse[D: Eq]: Eq[GraphQLResponse[D]] =
    Eq.by(x => (x.result, x.extensions))

  implicit val traverseGraphQLResponse: Traverse[GraphQLResponse] =
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
}
