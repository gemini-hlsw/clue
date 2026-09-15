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
  // `maxInputValueDepth = 16` mirrors the generator's parser config
  // (gen/rules/src/main/scala/clue/gen/package.scala), so the two agree on what parses.
  private val parser: GraphQLParser =
    GraphQLParser(
      GraphQLParser.defaultConfig.copy(terseError = false, maxInputValueDepth = 16)
    )

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

    val subqueryClass = TypeRepr.of[GraphQLSubquery[?]].typeSymbol

    // The `GraphQLSubquery` this splice is being expanded inside, if any: walking owners outwards,
    // the first class that is one. This is what makes subquery-into-subquery splices checkable — the
    // enclosing subquery's `VariableDefs` is the declaration the requirements are checked against.
    val enclosingSubquery: Option[Symbol] = {
      @scala.annotation.tailrec
      def loop(sym: Symbol): Option[Symbol] =
        if (sym.isNoSymbol) None
        else if (sym.isClassDef && sym.typeRef.baseClasses.contains(subqueryClass)) Some(sym)
        else loop(sym.owner)

      loop(Symbol.spliceOwner)
    }

    // Parse `text`, aborting the macro expansion at `pos` with `describe(problemMessages)` on a
    // parse failure. Any warnings are surfaced as compile warnings at `pos` before continuing;
    // grackle's parser does not currently produce warnings, so this is future-proofing.
    def parseOrAbort(text: String, pos: Position, describe: String => String): Ast.Document =
      parser.parseText(text) match {
        case Result.Success(doc)           => doc
        case Result.Warning(problems, doc) =>
          problems.toList.foreach(p => report.warning(describe(p.message), pos))
          doc
        case Result.Failure(problems)      =>
          report.errorAndAbort(
            describe(problems.toList.map(_.message).mkString("\n")),
            pos
          )
        case Result.InternalError(t)       =>
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

    // A document may declare more than one operation (clue's clients select one by `operationName`,
    // see `clue.FetchClientWithPars.request`), so each splice's declared variables are read from its
    // own enclosing operation (or, inside a fragment, the intersection of every operation's) rather
    // than from one flattened map — see `GraphQLDocuments.spliceScopes`.
    val scopesByIndex: Map[Int, Map[String, Ast.Type]] = GraphQLDocuments.spliceScopes(doc)
    val sitesByIndex: Map[Int, String]                 = GraphQLDocuments.spliceSites(doc)

    // Defensive fallback for a splice `spliceScopes` couldn't place (shouldn't happen): the union of
    // every operation's variables, same as the document-wide view used elsewhere for `VariableDefs`.
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

    // What the enclosing subquery (if any) declares in its own `type VariableDefs`. This is the same
    // for every splice in this `gql` call — it comes from the Scala class the splice is lexically
    // inside, not from `doc`'s own structure — and is layered under each splice's own in-doc scope
    // below.
    val enclosingVariableDefs: Map[String, Ast.Type] =
      enclosingSubquery
        .flatMap(cls => variableDefsOf(cls.typeRef).map(cls -> _))
        .map { case (cls, defs) =>
          parseVariableDefs(defs, cls.name.stripSuffix("$"), Position.ofMacroExpansion)
        }
        .getOrElse(Map.empty)

    // `@GraphQL` + `@GraphQLStub` is the generator's pre-generation form: the companion object is a
    // bare placeholder (no `GraphQLSubquery` supertype) that the scalafix rule replaces with a real
    // `GraphQLSubquery` before anything downstream compiles it (downstream builds compile only the
    // generated sources, via `clueSourceDirectory` feeding `sourceGenerators`). clue's own
    // `gen/input` fixtures are the one place those pre-generation sources are compiled by plain
    // scalac, so the macro must tolerate splicing the placeholder; the generated form still gets the
    // full check above. The annotation can surface on either the module value symbol or the module
    // class symbol, so both are checked.
    val stubAnnotation                          = TypeRepr.of[clue.annotation.GraphQLStub]
    def hasStubAnnotation(sym: Symbol): Boolean =
      sym.annotations.exists(_.tpe =:= stubAnnotation)

    argExprs.zipWithIndex.foreach { case (arg, i) =>
      val argTpe    = arg.asTerm.tpe
      val isStubbed = hasStubAnnotation(argTpe.typeSymbol) || hasStubAnnotation(argTpe.termSymbol)
      if (!argTpe.baseClasses.contains(subqueryClass) && !isStubbed)
        report.errorAndAbort(
          s"gql: only a GraphQLSubquery can be spliced into a document, found [${argTpe.show}]. " +
            "Build text by other means with GraphQLDocument.unsafeFromString.",
          arg.asTerm.pos
        )

      // What this particular splice site declares: the enclosing subquery's `VariableDefs` (if any)
      // plus whatever `doc`'s own structure puts in scope here (its enclosing operation's variables,
      // or — inside a fragment — the intersection of every operation's).
      val declaredVars: Map[String, Ast.Type] =
        enclosingVariableDefs ++ scopesByIndex.getOrElse(i, headerVariables)

      // Where the missing declaration has to be added. Inside a subquery it's always that subquery
      // (`name` keeps the module-class `$` suffix for an `object` subquery, which would only confuse
      // the reader), regardless of `doc`'s own internal structure; otherwise it's this splice's own
      // enclosing operation or fragment.
      val declarationSite: String =
        enclosingSubquery.fold(sitesByIndex.getOrElse(i, "operation"))(cls =>
          s"subquery [${cls.name.stripSuffix("$")}]"
        )

      variableDefsOf(argTpe).foreach { required =>
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
