// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.gen

import cats.data.NonEmptyChain
import cats.effect.IO
import cats.syntax.all.*
import grackle.Problem
import grackle.Result
import grackle.Schema
import scalafix.lint.Diagnostic
import scalafix.lint.LintSeverity
import scalafix.v1.*

import scala.meta.*

/**
 * Validation machinery shared by the `GraphQLGen` (generation) and `GraphQLValidate`
 * (validation-only) rules: it turns a parsed operation/subquery into scalafix diagnostics.
 */
trait GraphQLValidation extends QueryGen {

  protected def config: GraphQLGenConfig

  // Matches any object/trait/class definition, extracting its mods, parents and body. Used to
  // detect hand-written `GraphQLOperation`/`GraphQLSubquery`s.
  protected object GraphQLOperationDefn {
    def unapply(tree: Tree): Option[(List[Mod], List[Init], List[Stat])] =
      tree match {
        case Defn.Object(mods, _, Template.Initial(_, inits, _, stats))              =>
          (mods, inits, stats).some
        case Defn.Trait.Initial(mods, _, _, _, Template.Initial(_, inits, _, stats)) =>
          (mods, inits, stats).some
        case Defn.Class.Initial(mods, _, _, _, Template.Initial(_, inits, _, stats)) =>
          (mods, inits, stats).some
        case _                                                                       =>
          none
      }
  }

  protected def hasGraphQLAnnotation(mods: List[Mod]): Boolean =
    GraphQLAnnotation.unapply(mods).isDefined ||
      GraphQLSchemaAnnotation.unapply(mods).isDefined ||
      GraphQLStubAnnotation.unapply(mods).isDefined

  protected def lintProblems(
    problems: NonEmptyChain[Problem],
    severity: LintSeverity,
    pos:      Position
  ): Patch =
    problems.toList
      .map(problem => Patch.lint(Diagnostic("", problem.toString, pos, severity = severity)))
      .asPatch

  // A single error diagnostic anchored at `pos`.
  protected def errorDiagnostic(message: String, pos: Position): Patch =
    Patch.lint(Diagnostic("", message, pos, severity = LintSeverity.Error))

  // A single warning diagnostic anchored at `pos`.
  protected def warningDiagnostic(message: String, pos: Position): Patch =
    Patch.lint(Diagnostic("", message, pos, severity = LintSeverity.Warning))

  // Loads the named schema and runs `body`, reporting schema problems as diagnostics anchored at
  // `pos` rather than aborting the whole run. A failure (missing or unparseable schema) reports
  // errors and skips `body`; schema parse warnings are reported but `body` still runs.
  protected def withSchema(name: String, pos: Position)(body: Schema => Patch): IO[Patch] =
    config.getSchema(name).map {
      _.fold(
        failure = problems => lintProblems(problems, LintSeverity.Error, pos),
        success = schema => body(schema),
        warning =
          (problems, schema) => lintProblems(problems, LintSeverity.Warning, pos) + body(schema),
        error = t => errorDiagnostic(t.getMessage, pos)
      )
    }

  // Position of the named value's right-hand side (e.g. the `document`/`subquery` literal), used to
  // anchor query diagnostics at the query itself rather than at the enclosing definition.
  protected def gqlValuePos(name: String, stats: List[Stat]): Option[Position] =
    stats.collectFirst { case Defn.Val(_, List(Pat.Var(Term.Name(`name`))), _, rhs) =>
      rhs.pos
    }

  // Turns a validation result into lint diagnostics. Errors are anchored at `errorPos` (the query
  // value), warnings at `warningPos` (the enclosing definition) — query warnings such as deprecation
  // read better at the operation, and it keeps them assertable even for multi-line query literals.
  protected def lintResult(result: Result[Unit], errorPos: Position, warningPos: Position): Patch =
    result.fold(
      failure = problems => lintProblems(problems, LintSeverity.Error, errorPos),
      success = _ => Patch.empty,
      warning = (problems, _) => lintProblems(problems, LintSeverity.Warning, warningPos),
      error = t => errorDiagnostic(t.getMessage, errorPos)
    )

  protected def isGraphQLOperationDefn(inits: List[Init], stats: List[Stat]): Boolean =
    extractSchemaType(inits).isDefined && extractDocument(stats).isDefined

  // A subquery whose body we recognize (extends `GraphQLSubquery[.Typed]` and has a `subquery`).
  protected def isGraphQLSubqueryDefn(inits: List[Init], stats: List[Stat]): Boolean =
    extractSubquerySchemaType(inits).isDefined && extractSubquery(stats).isDefined

  // Validates a definition that is a `GraphQLOperation[.Typed]` (has a `document`) or a
  // `GraphQLSubquery[.Typed]` (has a `subquery`), producing diagnostics. Returns no patch if the
  // definition is neither. `defnPos` anchors structural/schema/warning diagnostics.
  //
  // `generating` is true when this definition is processed by the code generator (it carries a
  // `@GraphQL` annotation): in that case a subquery must declare exactly one `@GraphQLType`, since
  // generation (whether it synthesizes `Data` or copies a `.Typed`) targets a single type. When
  // false (hand-written, validation only), multiple types are allowed and the selection is validated
  // against each.
  protected def validateGraphQLDefn(
    mods:       List[Mod],
    inits:      List[Init],
    stats:      List[Stat],
    defnPos:    Position,
    generating: Boolean
  ): IO[Patch] = {
    val rootTypes = extractRootTypes(mods)

    extractSchemaType(inits).zip(extractDocument(stats)) match {
      // A `GraphQLOperation`. `@GraphQLType` is meaningless here (it declares a subquery's root type).
      case Some((schemaType, document)) =>
        if (rootTypes.nonEmpty)
          IO.pure(
            errorDiagnostic(
              "@GraphQLType is only valid on a GraphQLSubquery, not on a GraphQLOperation.",
              defnPos
            )
          )
        else
          withSchema(schemaType.value, defnPos) { schema =>
            lintResult(
              validateDocument(schema, document.render),
              gqlValuePos("document", stats).getOrElse(defnPos),
              defnPos
            )
          }
      case None                         =>
        extractSubquerySchemaType(inits).zip(extractSubquery(stats)) match {
          case Some((schemaType, subquery)) =>
            if (rootTypes.isEmpty)
              // No `@GraphQLType`: we can't determine the root type, so we can't validate. Warn
              // rather than silently skip.
              IO.pure(
                warningDiagnostic(
                  "Cannot validate subquery: add a `@GraphQLType(\"...\")` annotation declaring its " +
                    "root type(s).",
                  defnPos
                )
              )
            else if (generating && rootTypes.sizeIs > 1)
              IO.pure(
                errorDiagnostic(
                  "A subquery processed by the generator (@GraphQL) must declare exactly one " +
                    "@GraphQLType; multiple types are only supported on hand-written subqueries.",
                  defnPos
                )
              )
            else
              withSchema(schemaType.value, defnPos) { schema =>
                lintResult(
                  validateSubqueryTypes(
                    schema,
                    rootTypes,
                    subqueryVariableDefs(schema,
                                         rootTypes,
                                         stats,
                                         subquery.render,
                                         infer = generating
                    ),
                    subquery.render
                  ),
                  gqlValuePos("subquery", stats).getOrElse(defnPos),
                  defnPos
                )
              }
          case None                         =>
            IO.pure(Patch.empty)
        }
    }
  }
}
