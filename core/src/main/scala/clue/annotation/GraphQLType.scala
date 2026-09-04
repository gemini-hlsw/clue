// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.annotation

import scala.annotation.StaticAnnotation

/**
 * Declares the GraphQL root type(s) a `GraphQLSubquery`'s selection set applies to, so it can be
 * validated against the schema. Multiple types are allowed for hand-written subqueries (the
 * selection is validated against each); a subquery processed by the generator (`@GraphQL`) must
 * declare exactly one. Not valid on a `GraphQLOperation`.
 */
class GraphQLType(types: String*) extends StaticAnnotation
