// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.effect.IO
import grackle.GraphQLParser
import grackle.QueryParser

package object gen {
  def abort(msg: String): IO[Nothing] =
    IO.raiseError(new Exception(msg))

  def log(msg: String): IO[Unit] = IO(println(msg))

  private val config = GraphQLParser.defaultConfig.copy(maxInputValueDepth = 16)

  val GQLParser: QueryParser = QueryParser(GraphQLParser(config))
}
