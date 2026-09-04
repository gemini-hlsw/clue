// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import io.circe.Decoder

/*
 * A subquery must extend this trait. The GraphQL root type(s) the subquery applies to are declared
 * via the `@clue.annotation.GraphQLType` annotation (used for schema validation), not constructor
 * arguments.
 *
 * A subquery that references GraphQL variables declares them in a `type VariableDefs = "(...)"`
 * member (parenthesized var-defs, operation-header syntax). The member is not declared here: absent
 * means the subquery references no variables. It is read by name — syntactically by the generator,
 * and at compile time by the `gql` interpolator, which checks it against every splice site.
 */
abstract class GraphQLSubquery[S] extends GraphQLTextSyntax {
  type Data

  val dataDecoder: Decoder[Data]

  // Built with `gql"..."`, which checks that any subquery spliced in here has its variables declared
  // by this subquery's `VariableDefs`.
  val subquery: GraphQLDocument

  object givens {
    given Decoder[Data] = dataDecoder
  }

  final override def toString = subquery.value
}

object GraphQLSubquery {
  abstract class Typed[S, T: Decoder] extends GraphQLSubquery[S] {
    override type Data = T
    override val dataDecoder = summon[Decoder[T]]
  }
}
