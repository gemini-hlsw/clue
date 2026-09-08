// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

/**
 * Text-level scanning of GraphQL documents, shared by the `gql` macro. Kept free of macro APIs so
 * it can be unit-tested directly.
 *
 * This is deliberately not a GraphQL parser: `core` has no grackle dependency, and the macro only
 * sees literal fragments with holes where subqueries are spliced. The scans are just precise enough
 * for the variable and fragment checks, and only ever err on the side of silence (e.g. a `$name`
 * inside a GraphQL string literal counts as a usage).
 */
private[clue] object GraphQLText {

  /** A document's literal parts split into the header's declarations and the body text. */
  final case class Parsed(operationVars: Map[String, String], body: String)

  /**
   * Split the `gql` literal parts (as `StringContext.parts`, `$$`-escaped) into the operation
   * header's variable declarations and the body text where usages and fragment spreads live. The
   * header is excluded from the body so its `$name` declarations don't count as usages.
   */
  def parse(parts: List[String]): Parsed = {
    val firstPart = parts.headOption.getOrElse("").replace("$$", "$")
    val header    = headerSpan(firstPart)

    val operationVars = header.fold(Map.empty[String, String]) { case (open, end) =>
      parseVarDefs(firstPart.substring(open, end + 1))
    }
    val body          =
      header.fold(firstPart) { case (_, end) => firstPart.substring(end + 1) } +
        parts.drop(1).map(_.replace("$$", "$")).mkString(" ")

    Parsed(operationVars, body)
  }

  /**
   * Span `(open, end)` of the operation header's var-defs in `query (...) { ... }`, if any. A `(`
   * after the first `{` belongs to a field's arguments, not a header: a subquery body like
   * `{ hero(episode: $ep) }` has none.
   */
  def headerSpan(h: String): Option[(Int, Int)] = {
    val open  = h.indexOf('(')
    val brace = h.indexOf('{')
    if (open < 0 || (brace >= 0 && brace < open)) None
    else {
      var depth = 0; var i = open; var end = -1
      while (i < h.length && end < 0) {
        h.charAt(i) match {
          case '(' => depth += 1
          case ')' => depth -= 1; if (depth == 0) end = i
          case _   => ()
        }
        i += 1
      }
      if (end < 0) None else Some((open, end))
    }
  }

  /** Parse a parenthesized var-def list `($a: T, $b: U)` into name -> GraphQL type. */
  def parseVarDefs(s0: String): Map[String, String] = {
    val s = s0.trim.stripPrefix("(").stripSuffix(")").trim.replace("$$", "$")
    if (s.isEmpty) Map.empty
    else
      splitTopLevel(s).flatMap { entry =>
        val e       = entry.trim
        val nameEnd = e.indexOf(':')
        if (nameEnd < 0 || !e.startsWith("$")) None
        else {
          val name = e.substring(1, nameEnd).trim
          val tpe  = e.substring(nameEnd + 1).takeWhile(_ != '=').trim
          Some(name -> tpe)
        }
      }.toMap
  }

  private def splitTopLevel(s: String): List[String] = {
    val out   = scala.collection.mutable.ListBuffer.empty[String]
    val cur   = new StringBuilder
    var depth = 0
    s.foreach {
      case '['               => depth += 1; cur += '['
      case ']'               => depth -= 1; cur += ']'
      case ',' if depth == 0 => out += cur.toString; cur.clear()
      case c                 => cur += c
    }
    if (cur.nonEmpty) out += cur.toString
    out.toList
  }

  /**
   * GraphQL "is variable usage allowed": the declared type must be usable where the required type
   * is expected. Same base type, and a non-null requirement needs a non-null declared type.
   */
  def usableAs(declaredType: String, reqType: String): Boolean = {
    val d = declaredType.trim; val r = reqType.trim
    d.stripSuffix("!").trim == r.stripSuffix("!").trim && (!r.endsWith("!") || d.endsWith("!"))
  }

  private val VarRef      = """\$(\w+)""".r
  private val FragmentDef = """\bfragment\s+(\w+)\s+on\b""".r
  // `... on Type` is an inline fragment, not a spread.
  private val FragmentUse = """\.\.\.\s*(?!on\b)(\w+)""".r

  /**
   * Declared variables that are neither referenced in `body` nor required by a spliced subquery
   * (GraphQL "All Variables Used"). Sorted for deterministic reporting.
   */
  def unusedVariables(
    declared:          Set[String],
    body:              String,
    requiredBySplices: Set[String]
  ): List[String] = {
    val used = VarRef.findAllMatchIn(body).map(_.group(1)).toSet ++ requiredBySplices
    (declared -- used).toList.sorted
  }

  /**
   * Fragments defined in `body` but never spread there (GraphQL "Fragments Must Be Used"). Spliced
   * subqueries are validated in isolation, so they can neither spread nor define the host's
   * fragments; the text is the whole story.
   */
  def unusedFragments(body: String): List[String] = {
    val defined = FragmentDef.findAllMatchIn(body).map(_.group(1)).toSet
    val spread  = FragmentUse.findAllMatchIn(body).map(_.group(1)).toSet
    (defined -- spread).toList.sorted
  }
}
