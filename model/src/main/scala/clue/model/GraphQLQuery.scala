// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model

import cats.Eq

opaque type GraphQLQuery = String

object GraphQLQuery:
  def apply(query: String): GraphQLQuery = query

  // Matched at a line start, so leading comments and fragment definitions are skipped. Spelled
  // `(?:^|\n)` rather than `(?m)^`, which Scala.js rejects unless the linker targets ES2018+.
  private val OperationType = """(?:^|\n)[ \t]*(query|mutation|subscription)\b""".r

  private val NamedOperation = """^(?:query|mutation|subscription)\s+(\w+)(?=\s*[({@])""".r

  private val FirstField = """\{(?:.|\s)*?(\w+)""".r

  // Paired with the document from the keyword onwards, so nothing preceding it can be mistaken for
  // the operation. A bare selection set is an anonymous query per the spec.
  private def operation(query: GraphQLQuery): Option[(String, String)] =
    OperationType
      .findFirstMatchIn(query)
      .map(m => (m.group(1), query.substring(m.start(1))))
      .orElse:
        val trimmed = query.trim
        Option.when(trimmed.startsWith("{"))(("query", trimmed))

  extension (query: GraphQLQuery)
    def value: String = query

    /** The operation type keyword: `query`, `mutation` or `subscription`. */
    def operationType: Option[String] = operation(query).map(_._1)

    def querySummary: String =
      operation(query) match
        case Some((opType, doc)) =>
          val name =
            NamedOperation
              .findPrefixMatchOf(doc)
              .map(_.group(1))
              .orElse(FirstField.findFirstMatchIn(doc).map(_.group(1)))
          s"$opType-${name.getOrElse("<queryName?>")}"
        case None                => "<queryType?>-<queryName?>"

  inline given Eq[GraphQLQuery] = Eq.catsKernelInstancesForString
