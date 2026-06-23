// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.gen

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import metaconfig.Configured
import scalafix.v1.*

import scala.meta.*

/**
 * Validation-only counterpart of [[GraphQLGen]] (used by `sbt-clue`'s `clueCheck`). It validates
 * hand-written operations/subqueries against the schema but never generates code; an `@GraphQL`
 * family annotation here is reported as misplaced (those are only processed by the generator in the
 * clue source directory).
 *
 * It reads the shared `Clue` configuration (schema locations), the same key the generator uses.
 */
class GraphQLValidate(val config: GraphQLGenConfig)
    extends SemanticRule("GraphQLValidate")
    with GraphQLValidation {
  def this() = this(GraphQLGenConfig())

  override def withConfiguration(configuration: Configuration): Configured[Rule] =
    configuration.conf
      .getOrElse("Clue")(this.config)
      .map(newConfig => new GraphQLValidate(newConfig))

  override def fix(implicit doc: SemanticDocument): Patch =
    doc.tree
      .collect {
        // `@GraphQL` / `@GraphQLSchema` / `@GraphQLStub` are only processed by the code generator in
        // the clue source directory. Anywhere else they're misplaced — report that.
        case obj @ GraphQLOperationDefn(mods, _, _) if hasGraphQLAnnotation(mods) =>
          IO.pure(
            warningDiagnostic(
              "@GraphQL / @GraphQLSchema / @GraphQLStub are only processed by the code generator in " +
                "the clue source directory (e.g. src/clue/scala). This annotation has no effect here.",
              obj.pos
            )
          )
        // Hand-written operations/subqueries: validate against the schema.
        case obj @ GraphQLOperationDefn(mods, inits, stats)
            if !hasGraphQLAnnotation(mods) &&
              (isGraphQLOperationDefn(inits, stats) || isGraphQLSubqueryDefn(inits, stats)) =>
          validateGraphQLDefn(mods, inits, stats, obj.pos, generating = false)
      }
      .sequence
      .unsafeRunSync()
      .asPatch
}
