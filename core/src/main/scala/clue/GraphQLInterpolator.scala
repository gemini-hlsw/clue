// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.syntax.foldable.*
import grackle.Ast
import grackle.GraphQLParser
import grackle.Result

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
  }
}

extension (inline sc:         StringContext)
  /**
   * Builds an operation `document` or a subquery's `subquery`, splicing subqueries inline. At
   * runtime it produces exactly the string the standard `s"..."` interpolator would.
   *
   * At compile time the assembled document (with each splice stood in for by a placeholder
   * selection) is parsed with grackle's parser: a syntax error is a compile error. Fragments may
   * precede the operation. `GraphQLDocument.unsafeFromString` remains the escape hatch for text
   * built by other means, which skips both the parse and the check below.
   *
   * The macro also runs the *caller-check*: for every spliced value that declares variables (a
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

  // `terseError = false` so parse failures render readable, multi-line messages.
  private val parser: GraphQLParser =
    GraphQLParser(GraphQLParser.defaultConfig.copy(terseError = false))

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

    // Parse `text`, aborting the macro expansion at `pos` with `describe(problemMessages)` on a
    // parse failure.
    def parseOrAbort(text: String, pos: Position, describe: String => String): Ast.Document =
      parser.parseText(text) match {
        case Result.Success(doc)      => doc
        case Result.Warning(_, doc)   => doc
        case Result.Failure(problems) =>
          report.errorAndAbort(
            describe(problems.toList.map(_.message).mkString("\n")),
            pos
          )
        case Result.InternalError(t)  =>
          report.errorAndAbort(describe(s"parser error: ${t.getMessage}"), pos)
      }

    // The assembled document, with each splice stood in for by a placeholder selection (a spliced
    // subquery always stands where a selection set goes, so this keeps the document parseable
    // without knowing what's actually spliced in).
    val doc: Ast.Document =
      parseOrAbort(
        GraphQLDocuments.placeholderDocument(parts),
        Position.ofMacroExpansion,
        msg => s"gql: $msg"
      )

    // What the operation's header declares. A shorthand query, or a document with only fragments,
    // declares none. Fragments before the operation are naturally fine, since this looks at every
    // `Operation` in the document rather than just its first literal part.
    val headerVariables: Map[String, Ast.Type] = GraphQLDocuments.headerVariables(doc)

    // Parse a `VariableDefs` string (e.g. `"($ep: Episode!)"`) belonging to `ownerName`, the same
    // way, and return its variables.
    def parseVariableDefs(defs: String, ownerName: String, pos: Position): Map[String, Ast.Type] =
      GraphQLDocuments.headerVariables(
        parseOrAbort(
          GraphQLDocuments.wrapVariableDefs(defs),
          pos,
          msg => s"gql: invalid VariableDefs [$defs] on $ownerName: $msg"
        )
      )

    // What the splice site declares. An operation declares its variables in the document header; a
    // subquery declares them in `type VariableDefs`. Both are read (they are never both populated in
    // practice), so a splice is checked against everything in scope where it happens.
    val enclosingVariableDefs: Map[String, Ast.Type] =
      enclosingSubquery
        .flatMap(cls => variableDefsOf(cls.typeRef).map(cls -> _))
        .map { case (cls, defs) =>
          parseVariableDefs(defs, cls.name.stripSuffix("$"), Position.ofMacroExpansion)
        }
        .getOrElse(Map.empty)

    val declaredVars: Map[String, Ast.Type] = enclosingVariableDefs ++ headerVariables

    // Where the missing declaration has to be added. `name` keeps the module-class `$` suffix for an
    // `object` subquery, which would only confuse the reader.
    val declarationSite: String =
      enclosingSubquery.fold("operation")(cls => s"subquery [${cls.name.stripSuffix("$")}]")

    argExprs.foreach { arg =>
      variableDefsOf(arg.asTerm.tpe).foreach { required =>
        val ownerName    = arg.asTerm.tpe.typeSymbol.name.stripSuffix("$")
        val requiredVars = parseVariableDefs(required, ownerName, arg.asTerm.pos)
        requiredVars.foreach { case (name, reqType) =>
          declaredVars.get(name) match {
            case None                                                            =>
              report.errorAndAbort(
                s"gql: $declarationSite does not declare variable $$$name required by a spliced subquery (needs ${reqType.name})",
                arg.asTerm.pos
              )
            case Some(declared) if !GraphQLDocuments.usableAs(declared, reqType) =>
              report.errorAndAbort(
                s"gql: $declarationSite declares $$$name: ${declared.name} but a spliced subquery requires it usable as ${reqType.name}",
                arg.asTerm.pos
              )
            case _                                                               => ()
          }
        }
      }
    }

    // Runtime string, identical to `s"..."`, wrapped as a GraphQLDocument.
    '{ GraphQLDocument.unsafeFromString($scExpr.s(${ Varargs(argExprs) }*)) }
  }
}
