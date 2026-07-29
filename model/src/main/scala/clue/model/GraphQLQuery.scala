// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model

import cats.Eq

opaque type GraphQLQuery = String

object GraphQLQuery:
  def apply(query: String): GraphQLQuery = query

  // The operation type keyword, matched at the start of a line so that leading comments and
  // fragment definitions are skipped.
  private val OperationType = """(?m)^[ \t]*(query|mutation|subscription)\b""".r

  // The name of an explicitly named operation: the word right after the operation type keyword,
  // followed by variable definitions, a directive or the selection set.
  private val NamedOperation = """^(?:query|mutation|subscription)\s+(\w+)(?=\s*[({@])""".r

  // Fallback for anonymous operations: the first word after the first '{'.
  private val FirstField = """\{(?:.|\s)*?(\w+)""".r

  // The operation type, paired with the document from the operation keyword onwards, so that
  // anything preceding it (comments, fragment definitions) cannot be mistaken for the operation.
  // A document that is a bare selection set is an anonymous query per the spec.
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
