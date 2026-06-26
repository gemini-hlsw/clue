// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import scala.quoted.*

extension (inline sc: StringContext)
  /**
   * Builds a GraphQL operation `document`, splicing subqueries inline. At runtime it produces exactly
   * the string the standard `s"..."` interpolator would. At compile time it runs the *caller-check*:
   * for every spliced value that declares variables (a `GraphQLSubquery` with a `type Variables`
   * member), it verifies the operation's `query (...)` header declares each required variable with a
   * compatible ("usable as") type — reading the requirement straight from the subquery's type, so it
   * works even when the subquery is shipped in a dependency jar.
   */
  inline def gql(inline args: Any*): String = ${ GraphQLInterpolator.gqlImpl('sc, 'args) }

private[clue] object GraphQLInterpolator {

  def gqlImpl(scExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Quotes): Expr[String] = {
    import quotes.reflect.*

    val parts: List[String] = scExpr match {
      case '{ StringContext(${ Varargs(ps) }*) } => ps.map(_.valueOrAbort).toList
      case _                                     =>
        report.errorAndAbort("gql: the string parts must be literals")
    }

    val argExprs: List[Expr[Any]] = argsExpr match {
      case Varargs(es) => es.toList
      case _           => report.errorAndAbort("gql: splice arguments must be explicit")
    }

    // Read a spliced value's `Variables` literal-type member, if it has one (a `GraphQLSubquery`).
    def variablesOf(arg: Expr[Any]): Option[String] = {
      val tpe = arg.asTerm.tpe
      val sym = tpe.typeSymbol.typeMember("Variables")
      if (sym.isNoSymbol) None
      else {
        // A type alias surfaces as `TypeBounds(lo, hi)` with `lo == hi`; read `hi`.
        val resolved = tpe.memberType(sym) match {
          case TypeBounds(_, hi) => hi.dealias
          case other             => other.dealias
        }
        resolved match {
          case ConstantType(StringConstant(s)) => Some(s)
          case _                               => None
        }
      }
    }

    def splitTopLevel(s: String): List[String] = {
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

    // Parse a parenthesized var-def list `($a: T, $b: U)` into name -> GraphQL type.
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

    // Extract and parse the operation header `(...)` from `query (...) ...` (the first literal part).
    def operationVars(header: String): Map[String, String] = {
      val h    = header.replace("$$", "$")
      val open = h.indexOf('(')
      if (open < 0) Map.empty
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
        if (end < 0) Map.empty else parseVarDefs(h.substring(open, end + 1))
      }
    }

    // GraphQL "is variable usage allowed": the provided type must be usable where the required type
    // is expected. Same base type, and a non-null requirement needs a non-null provided type.
    def usableAs(opType: String, reqType: String): Boolean = {
      val o = opType.trim; val r = reqType.trim
      o.stripSuffix("!").trim == r.stripSuffix("!").trim && (!r.endsWith("!") || o.endsWith("!"))
    }

    val opVars: Map[String, String] = parts.headOption.map(operationVars).getOrElse(Map.empty)

    argExprs.foreach { arg =>
      variablesOf(arg).foreach { required =>
        parseVarDefs(required).foreach { case (name, reqType) =>
          opVars.get(name) match {
            case None                                       =>
              report.errorAndAbort(
                s"gql: operation does not declare variable $$$name required by a spliced subquery (needs $reqType)",
                arg.asTerm.pos
              )
            case Some(opType) if !usableAs(opType, reqType) =>
              report.errorAndAbort(
                s"gql: operation declares $$$name: $opType but a spliced subquery requires it usable as $reqType",
                arg.asTerm.pos
              )
            case _                                          => ()
          }
        }
      }
    }

    // Runtime string: identical to `s"..."`.
    '{ $scExpr.s(${ Varargs(argExprs) }*) }
  }
}
