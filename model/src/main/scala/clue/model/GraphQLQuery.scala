// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model

import cats.Eq
import cats.syntax.option.*

opaque type GraphQLQuery = String

object GraphQLQuery:
  def apply(query: String): GraphQLQuery = query

  private val QueryTypeAndName = "(\\w+).*\\{(?:.|\\s)*?(\\w+)".r.unanchored

  private def queryTypeAndName(query: GraphQLQuery): Option[(String, String)] =
    query match
      case QueryTypeAndName(queryType, queryName) => (queryType, queryName).some
      case _                                      => none

  extension (query: GraphQLQuery)
    def value: String        = query
    def querySummary: String =
      val typeAndName: Option[(String, String)] = queryTypeAndName(query)
      s"${typeAndName.map(_._1).getOrElse("<queryType?>")}-${typeAndName.map(_._2).getOrElse("<queryName?>")}"

  given Eq[GraphQLQuery] = Eq.by(_.value)
