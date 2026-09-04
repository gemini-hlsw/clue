// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import grackle.GraphQLParser
import grackle.QueryParser

package object gen {
  private val config = GraphQLParser.defaultConfig.copy(maxInputValueDepth = 16)

  val GQLParser: QueryParser = QueryParser(GraphQLParser(config))
}
