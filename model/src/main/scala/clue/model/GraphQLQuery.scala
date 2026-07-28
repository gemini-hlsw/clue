// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model

import cats.Eq

opaque type GraphQLQuery = String

object GraphQLQuery:
  def apply(query: String): GraphQLQuery = query

  // The operation type keyword at the start of the document (query/mutation/subscription).
  private val OperationType = """^\s*(\w+)""".r

  // An explicitly-named operation
  private val NamedOperation = """^\s*\w+\s+(\w+)(?=\s*[({])""".r

  // Fallback for anonymous operations: the first word after the first '{'.
  private val FirstField = """\{(?:.|\s)*?(\w+)""".r

  private def queryTypeAndName(query: GraphQLQuery): Option[(String, String)] =
    val doc  = query.trim
    val tpe  = OperationType.findFirstMatchIn(doc).map(_.group(1))
    val name =
      NamedOperation
        .findFirstMatchIn(doc)
        .map(_.group(1))
        .orElse(FirstField.findFirstMatchIn(doc).map(_.group(1)))
    tpe.map((_, name.getOrElse("<queryName?>")))

  extension (query: GraphQLQuery)
    def value: String        = query
    def querySummary: String =
      queryTypeAndName(query) match
        case Some((tpe, name)) => s"$tpe-$name"
        case None              => "<queryType?>-<queryName?>"

  inline given Eq[GraphQLQuery] = Eq.catsKernelInstancesForString
