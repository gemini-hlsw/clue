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
   * At compile time the assembled document (all literal parts, with spliced holes) is lexed per the
   * GraphQL spec — a lexical error (an unterminated string, an unknown character, ...) is a compile
   * error — and the *caller-check* runs over the resulting tokens: for every spliced value that
   * declares variables (a `GraphQLSubquery` with a `type VariableDefs` member), it verifies that
   * the variables are declared where the splice happens, with a compatible ("usable as") type. The
   * declaration is read from the operation's `query (...)` header — found by scanning tokens for
   * the `query`/`mutation`/`subscription` keyword at brace-depth 0, so fragment definitions may
   * precede the operation without its header being missed — or, when the splice happens inside a
   * `GraphQLSubquery`, from that subquery's own `type VariableDefs`, so nesting is checked one
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

    // Parse a `VariableDefs` literal (`"($a: T, ...)"`, hand-written on a `GraphQLSubquery`, unlike
    // the document itself) into its declared types, aborting with a compile error naming `ownerName`
    // if it doesn't lex/parse.
    def parseVariableDefs(
      s:         String,
      ownerName: String
    ): Map[String, GraphQLDocumentScan.VarType] = {
      val parsed = for {
        tokens <- GraphQLLexer.tokenize(s).left.map(_.message)
        vars   <- GraphQLDocumentScan.parseVarDefs(tokens).left.map(_.message)
      } yield vars
      parsed match {
        case Right(vars) => vars
        case Left(msg)   =>
          report.errorAndAbort(s"gql: invalid VariableDefs [$s] on $ownerName: $msg")
      }
    }

    // Lex the whole document (literal parts + splice holes) and find the operation header's
    // var-defs. Scanning tokens (rather than the old `indexOf('(')` on raw strings) means a fragment
    // preceding the operation, or a string/comment containing `(`, can't be mistaken for the header.
    val tokens: Vector[GraphQLLexer.Token] =
      GraphQLLexer.tokenizeParts(parts) match {
        case Right(ts) => ts
        case Left(err) => report.errorAndAbort(s"gql: ${err.message}", Position.ofMacroExpansion)
      }

    val headerVars: Map[String, GraphQLDocumentScan.VarType] =
      GraphQLDocumentScan.operationVars(tokens) match {
        case Right((vars, _)) => vars
        case Left(err)        => report.errorAndAbort(s"gql: ${err.message}", Position.ofMacroExpansion)
      }

    // What the splice site declares. An operation declares its variables in the document header; a
    // subquery declares them in `type VariableDefs`. Both are read (they are never both populated in
    // practice), so a splice is checked against everything in scope where it happens.
    val enclosingVariableDefs: Map[String, GraphQLDocumentScan.VarType] =
      enclosingSubquery
        .flatMap(cls =>
          variableDefsOf(cls.typeRef).map(parseVariableDefs(_, cls.name.stripSuffix("$")))
        )
        .getOrElse(Map.empty)

    val declaredVars: Map[String, GraphQLDocumentScan.VarType] = enclosingVariableDefs ++ headerVars

    // Where the missing declaration has to be added. `name` keeps the module-class `$` suffix for an
    // `object` subquery, which would only confuse the reader.
    val declarationSite: String =
      enclosingSubquery.fold("operation")(cls => s"subquery [${cls.name.stripSuffix("$")}]")

    argExprs.foreach { arg =>
      val argTpe = arg.asTerm.tpe
      variableDefsOf(argTpe).foreach { requiredDefs =>
        val required = parseVariableDefs(requiredDefs, argTpe.typeSymbol.name.stripSuffix("$"))
        required.foreach { case (name, reqType) =>
          declaredVars.get(name) match {
            case None                                                               =>
              report.errorAndAbort(
                s"gql: $declarationSite does not declare variable $$$name required by a spliced subquery (needs ${reqType.render})",
                arg.asTerm.pos
              )
            case Some(declared) if !GraphQLDocumentScan.usableAs(declared, reqType) =>
              report.errorAndAbort(
                s"gql: $declarationSite declares $$$name: ${declared.render} but a spliced subquery requires it usable as ${reqType.render}",
                arg.asTerm.pos
              )
            case _                                                                  => ()
          }
        }
      }
    }

    // Runtime string, identical to `s"..."`, wrapped as a GraphQLDocument.
    '{ GraphQLDocument.unsafeFromString($scExpr.s(${ Varargs(argExprs) }*)) }
  }
}
