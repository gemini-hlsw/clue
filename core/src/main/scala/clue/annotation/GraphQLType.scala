// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.annotation

import scala.annotation.StaticAnnotation

/**
 * Declares the GraphQL root type(s) a `GraphQLSubquery`'s selection set applies to, so it can be
 * validated against the schema. When multiple types are declared, the selection is validated
 * against each. For a subquery processed by the generator (`@GraphQL`), the selected fields must
 * also have the same types in every declared type, since a single `Data` is generated. Not valid on
 * a `GraphQLOperation`.
 */
class GraphQLType(types: String*) extends StaticAnnotation
