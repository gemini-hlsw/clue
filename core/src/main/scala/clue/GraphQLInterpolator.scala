// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import scala.quoted.*

/**
 * Assembled GraphQL text: an operation's `document`, or a subquery's `subquery` selection set. The
 * only way to obtain one is the `gql` interpolator, so both are always run through the compile-time
 * caller-check — a plain `String` or `s"..."` does not type-check as a `GraphQLDocument`. Read the
 * underlying string with `.value`.
 */
opaque type GraphQLDocument = String
object GraphQLDocument {
  // `gql"..."` is the validated way to build one — a plain `String`/`s"..."` won't type-check as a
  // `GraphQLDocument`. `unsafeFromString` is the explicit escape hatch for text built by other
  // means; it skips the caller-check, so prefer `gql`.
  def unsafeFromString(value: String): GraphQLDocument = value

  extension (document: GraphQLDocument) {
    def value: String = document

    /**
     * As `String.stripMargin`. It runs on the assembled text, so it also strips margins inside
     * spliced subqueries — the same as calling `.stripMargin` on an `s"..."` interpolation.
     */
    def stripMargin: GraphQLDocument = stripMargin('|')

    def stripMargin(marginChar: Char): GraphQLDocument =
      // Explicit `StringOps` because inside this file `GraphQLDocument` is `String`, so a bare
      // `document.stripMargin` would resolve back to this extension.
      new scala.collection.StringOps(document).stripMargin(marginChar)
  }
}

extension (inline sc:         StringContext)
  /**
   * Builds an operation `document` or a subquery's `subquery`, splicing subqueries inline. At
   * runtime it produces exactly the string the standard `s"..."` interpolator would.
   *
   * At compile time it runs the *caller-check*: for every spliced value that declares variables (a
   * `GraphQLSubquery` with a `type VariableDefs` member), it verifies that the variables are
   * declared where the splice happens, with a compatible ("usable as") type. The declaration is
   * read from the operation's `query (...)` header, or — when the splice happens inside a
   * `GraphQLSubquery` — from that subquery's own `type VariableDefs`, so nesting is checked one
   * level at a time and holds transitively.
   *
   * Requirements are read straight off the spliced subquery's type, so the check also works when
   * the subquery is shipped in a dependency jar.
   */
  inline def gql(inline args: Any*): GraphQLDocument = ${ GraphQLInterpolator.gqlImpl('sc, 'args) }

/**
 * Makes `gql` available inside operation/subquery bodies without an import. `protected`, so it
 * doesn't leak into the public API of the operations and subqueries that inherit it.
 */
trait GraphQLTextSyntax {
  extension (inline sc: StringContext)
    protected inline def gql(inline args: Any*): GraphQLDocument = ${
      GraphQLInterpolator.gqlImpl('sc, 'args)
    }
}

private[clue] object GraphQLInterpolator {

  def gqlImpl(scExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using
    Quotes
  ): Expr[GraphQLDocument] = {
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

    // The variables a type declares in its `VariableDefs` member (i.e. it is a `GraphQLSubquery`
    // that references variables). Works for the enclosing class too, even though it is still being
    // typed while this splice expands: a literal type alias doesn't depend on the rest of the body.
    def variableDefsOf(tpe: TypeRepr): Option[String] = {
      val sym = tpe.typeSymbol.typeMember("VariableDefs")
      if (sym.isNoSymbol) None
      else
        // A type alias surfaces as `TypeBounds(lo, hi)` with `lo == hi`; read `hi`.
        (tpe.memberType(sym) match {
          case TypeBounds(_, hi) => hi.dealias
          case other             => other.dealias
        }) match {
          case ConstantType(StringConstant(s)) => Some(s)
          case _                               => None
        }
    }

    // The `GraphQLSubquery` this splice is being expanded inside, if any: walking owners outwards,
    // the first class that is one. This is what makes subquery-into-subquery splices checkable — the
    // enclosing subquery's `VariableDefs` is the declaration the requirements are checked against.
    val enclosingSubquery: Option[Symbol] = {
      val subqueryClass = TypeRepr.of[GraphQLSubquery[?]].typeSymbol

      @scala.annotation.tailrec
      def loop(sym: Symbol): Option[Symbol] =
        if (sym.isNoSymbol) None
        else if (sym.isClassDef && sym.typeRef.baseClasses.contains(subqueryClass)) Some(sym)
        else loop(sym.owner)

      loop(Symbol.spliceOwner)
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

    // GraphQL "is variable usage allowed": the declared type must be usable where the required type
    // is expected. Same base type, and a non-null requirement needs a non-null declared type.
    def usableAs(declaredType: String, reqType: String): Boolean = {
      val d = declaredType.trim; val r = reqType.trim
      d.stripSuffix("!").trim == r.stripSuffix("!").trim && (!r.endsWith("!") || d.endsWith("!"))
    }

    // What the splice site declares. An operation declares its variables in the document header; a
    // subquery declares them in `type VariableDefs`. Both are read (they are never both populated in
    // practice), so a splice is checked against everything in scope where it happens.
    val enclosingVariableDefs: Map[String, String] =
      enclosingSubquery
        .flatMap(cls => variableDefsOf(cls.typeRef))
        .map(parseVarDefs)
        .getOrElse(Map.empty)

    val declaredVars: Map[String, String] =
      enclosingVariableDefs ++ parts.headOption.map(operationVars).getOrElse(Map.empty)

    // Where the missing declaration has to be added. `name` keeps the module-class `$` suffix for an
    // `object` subquery, which would only confuse the reader.
    val declarationSite: String =
      enclosingSubquery.fold("operation")(cls => s"subquery [${cls.name.stripSuffix("$")}]")

    argExprs.foreach { arg =>
      variableDefsOf(arg.asTerm.tpe).foreach { required =>
        parseVarDefs(required).foreach { case (name, reqType) =>
          declaredVars.get(name) match {
            case None                                           =>
              report.errorAndAbort(
                s"gql: $declarationSite does not declare variable $$$name required by a spliced subquery (needs $reqType)",
                arg.asTerm.pos
              )
            case Some(declared) if !usableAs(declared, reqType) =>
              report.errorAndAbort(
                s"gql: $declarationSite declares $$$name: $declared but a spliced subquery requires it usable as $reqType",
                arg.asTerm.pos
              )
            case _                                              => ()
          }
        }
      }
    }

    // Runtime string, identical to `s"..."`, wrapped as a GraphQLDocument.
    '{ GraphQLDocument.unsafeFromString($scExpr.s(${ Varargs(argExprs) }*)) }
  }
}
