// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.gen

import cats.Endo
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import grackle.InputValue
import grackle.Result
import grackle.UntypedOperation
import metaconfig.Configured
import scalafix.v1.*

import scala.meta.*

class GraphQLGen(val config: GraphQLGenConfig)
    extends SemanticRule("GraphQLGen")
    with SchemaGen
    with GraphQLValidation {
  def this() = this(GraphQLGenConfig())

  override def withConfiguration(config: Configuration): Configured[Rule] =
    config.conf
      .getOrElse("Clue")(this.config)
      .map(newConfig => new GraphQLGen(newConfig))

  private def indented(asTree: Tree)(lines: String): String = {
    val indentCols    = asTree.pos match {
      case range: Position.Range => range.startColumn
      case _                     => 0
    }
    val newLineIndent = "\n" + " " * indentCols
    newLineIndent + lines.replaceAll("\\n", newLineIndent)
  }

  // A generated subquery with several root types emits a single object, so the definitions
  // generated from each of them (one entry per root type) must be the same. Reports the first
  // difference found, if any.
  private def generatedMismatch(bodies: List[(String, List[Stat])]): Option[String] = {
    def lines(body: List[Stat]): List[String] =
      body.flatMap(_.syntax.linesIterator.map(_.trim).toList)

    bodies match {
      case (firstType, firstBody) :: rest =>
        val firstLines: List[String] = lines(firstBody)
        rest.collectFirst {
          case (otherType, otherBody) if lines(otherBody) != firstLines =>
            val (first, other): (String, String) =
              firstLines
                .zipAll(lines(otherBody), "", "")
                .find { case (a, b) => a != b }
                .getOrElse(("", ""))
            s"The subquery generates different definitions for its root types [$firstType] " +
              s"(`$first`) and [$otherType] (`$other`). The fields it selects must have the same " +
              "types in every root type."
        }
      case Nil                            => none
    }
  }

  override def fix(implicit doc: SemanticDocument): Patch = {
    val importPatch: List[IO[Patch]] =
      doc.tokens.collect {
        case token @ scala.meta.tokens.Token.Comment(value) if value.trim.startsWith("gql:") =>
          IO.pure(Patch.replaceToken(token, value.trim.stripPrefix("gql:").trim))
      }.toList

    val genPatch: List[IO[Patch]] =
      doc.tree
        .collect {
          case obj @ Defn.Object(
                mods @ GraphQLAnnotation(_),
                name,
                Template.Initial(early, inits, self, stats)
              ) =>
            // Annotated objects already supply their own Variables/Data (e.g. via
            // GraphQLOperation.Typed or GraphQLSubquery.Typed), so there's nothing to generate. We
            // still validate the document/subquery, add the inferred `VariableDefs` if it's a
            // subquery that needs one, then copy the object stripping the @GraphQL annotation
            // (keeping any others, such as @GraphQLType).
            val newMods = GraphQLAnnotation.removeFrom(mods)

            def copied(body: List[Stat]): Patch =
              Patch.replaceTree(
                obj,
                indented(obj)(
                  q"..$newMods object $name ${buildTemplate(early, inits, (self.some, body))}".toString
                )
              ) + Patch.removeGlobalImport(GraphQLAnnotation.symbol)

            // Like a generated subquery, an annotated one gets its referenced variables inferred and
            // emitted as `type VariableDefs`, so the `gql` caller-check sees what it requires.
            val body: IO[List[Stat]] =
              (extractSubquerySchemaType(inits),
               extractSubquery(stats),
               extractRootTypes(mods)
              ) match {
                case (Some(schemaType), Some(subquery), rootTypes)
                    if rootTypes.nonEmpty && extractVariableDefs(stats).isEmpty =>
                  config
                    .getSchema(schemaType.value)
                    .map(
                      _.toOption
                        .flatMap(schema =>
                          resolveSubqueryVariableDefs(schema,
                                                      rootTypes,
                                                      none,
                                                      subquery.render
                          ).toOption
                        )
                        .fold(stats)(variableDefs =>
                          addVariableDefsTypeAlias(none, variableDefs)(stats)
                        )
                    )
                case _ => IO.pure(stats)
              }

            (validateGraphQLDefn(mods, inits, stats, obj.pos, generating = true), body)
              .mapN((diagnostics, newBody) => diagnostics + copied(newBody))
          case obj @ Defn.Object(
                GraphQLStubAnnotation(_),
                _,
                _
              ) =>
            // The placeholder goes away, and with it the import of its annotation, if any.
            IO.pure(
              Patch.replaceTree(obj, "") + Patch.removeGlobalImport(GraphQLStubAnnotation.symbol)
            )
          case obj @ Defn.Trait.Initial(
                mods @ GraphQLSchemaAnnotation(_),
                templateName,
                Nil,
                _,
                Template.Initial(early, inits, self, stats)
              ) =>
            val objName = templateName.value
            withSchema(objName, obj.pos) { schema =>
              val modObjDefs = scala.Function.chain(
                List(addScalars, addEnums(schema, config), addInputs(schema, config))
              )

              // Can't get RemoveUnused to remove the unused import, since rules in the same run are note applied incrementally.
              // See https://github.com/scalacenter/scalafix/issues/1204
              val newMods = GraphQLSchemaAnnotation.removeFrom(mods)

              Patch.replaceTree(
                obj,
                indented(obj)(
                  List(
                    q"sealed trait ${Type.Name(objName)}".toString,
                    q"..$newMods object ${Term
                        .Name(objName)} ${buildTemplate(early, inits, (self.some, modObjDefs(stats)))}".toString
                  ).mkString("\n")
                )
              ) + Patch.removeGlobalImport(GraphQLSchemaAnnotation.symbol)
            }
          case obj @ Defn.Trait.Initial(
                mods @ GraphQLAnnotation(_),
                templateName,
                Nil,
                _,
                Template.Initial(early, inits, self, stats)
              ) if inits.exists {
                case Init.Initial(
                      Type.Apply.Initial(Type.Name("GraphQLOperation"), _),
                      _,
                      _
                    ) =>
                  true
                case _ => false
              } =>
            val objName = templateName.value

            extractSchemaType(inits) match {
              case None                                       =>
                IO.pure(
                  errorDiagnostic(
                    "Invalid annotation target: must be a trait extending GraphQLOperation[Schema]",
                    obj.pos
                  )
                )
              case Some(_) if extractRootTypes(mods).nonEmpty =>
                IO.pure(
                  errorDiagnostic(
                    "@GraphQLType is only valid on a GraphQLSubquery, not on a GraphQLOperation.",
                    obj.pos
                  )
                )
              case Some(schemaType)                           =>
                extractDocument(stats) match {
                  case None           =>
                    IO.pure(
                      errorDiagnostic(
                        "The GraphQLOperation must define a 'val document' with a literal or `gql\"...\"` value.",
                        obj.pos
                      )
                    )
                  case Some(document) =>
                    withSchema(schemaType.value, obj.pos) { schema =>
                      // Validate the document against the schema first, reporting all problems as
                      // diagnostics (errors at the document, warnings at the definition).
                      val validation: Result[Unit] = validateDocument(schema, document.render)
                      val diagnostics: Patch       =
                        lintResult(
                          validation,
                          gqlValuePos("document", stats).getOrElse(obj.pos),
                          obj.pos
                        )

                      // Only generate from a valid document. On errors we emit the diagnostics and
                      // leave the source untouched (a downstream compile error makes it obvious).
                      if (!validation.hasValue) diagnostics
                      else {
                        // Validation passed, so parsing and variable-type resolution below succeed.
                        val (operations, fragments) =
                          GQLParser.parseText(document.render).toOption.get

                        // TODO Support multi-operation queries?
                        val operation: UntypedOperation = operations.head

                        val inputs: List[InputValue] = computeVarDefs(schema, operation.variables)

                        // Modifications to add the missing definitions.
                        val modObjDefs: Endo[List[Stat]] =
                          scala.Function.chain(
                            List(
                              addImports(schemaType.value),
                              addVars(inputs, config),
                              addData(schema, operation, config, document.subqueries, fragments),
                              addVarEncoder,
                              addDataDecoder,
                              addConvenienceMethod(schemaType, operation, objName, config)
                            )
                          )

                        val newMods: List[Mod] = GraphQLAnnotation.removeFrom(mods)

                        // Congratulations! You got a full-fledged GraphQLOperation (hopefully).
                        diagnostics + Patch.replaceTree(
                          obj,
                          indented(obj)(
                            q"..$newMods object ${Term
                                .Name(
                                  objName
                                )} ${buildTemplate(early, inits, (self.some, modObjDefs(stats)))}".toString
                          )
                        ) + Patch.removeGlobalImport(GraphQLAnnotation.symbol)
                      }
                    }
                }
            }
          case obj @ Defn.Class.Initial(
                mods @ GraphQLAnnotation(_),
                templateName,
                Nil,
                _,
                Template.Initial(early, inits, self, stats)
              ) if inits.exists {
                case Init.Initial(
                      Type.Apply.Initial(Type.Name("GraphQLSubquery"), _),
                      _,
                      _
                    ) =>
                  true
                case _ => false
              } =>
            val objName = templateName.value

            (extractSubquerySchemaType(inits), extractRootTypes(mods)) match {
              case (None, _)                     =>
                IO.pure(
                  errorDiagnostic(
                    "Invalid annotation target: must be a class extending GraphQLSubquery[Schema].",
                    obj.pos
                  )
                )
              case (Some(_), Nil)                =>
                IO.pure(
                  errorDiagnostic(
                    "A GraphQLSubquery must declare its root type with a `@GraphQLType(\"...\")` annotation.",
                    obj.pos
                  )
                )
              case (Some(schemaType), rootTypes) =>
                extractSubquery(stats) match {
                  case None           =>
                    IO.pure(
                      errorDiagnostic(
                        "The GraphQLSubquery must define a 'val subquery' with a literal or `gql\"...\"` value.",
                        obj.pos
                      )
                    )
                  case Some(subquery) =>
                    withSchema(schemaType.value, obj.pos) { schema =>
                      val explicitVariableDefs: Option[String] = extractVariableDefs(stats)
                      // Validate the subquery against each of its root types first, reporting all
                      // problems as diagnostics (errors at the subquery, warnings at the
                      // definition). For @GraphQL subqueries, the referenced variables are inferred
                      // from usage when the author didn't declare them; the inferred set is emitted
                      // as `type VariableDefs`.
                      val validation: Result[String]           =
                        resolveSubqueryVariableDefs(
                          schema,
                          rootTypes,
                          explicitVariableDefs,
                          subquery.render
                        )
                      val diagnostics: Patch                   =
                        lintResult(
                          validation.map(_ => ()),
                          gqlValuePos("subquery", stats).getOrElse(obj.pos),
                          obj.pos
                        )

                      validation.toOption.fold(diagnostics) { variableDefs =>
                        // Validation passed, so parsing and variable-type resolution below succeed.
                        val (operations, fragments) =
                          GQLParser
                            .parseText(s"query $variableDefs ${subquery.render}")
                            .toOption
                            .get
                        // TODO Support multi-operation queries?
                        val operation               = operations.head

                        // Modifications to add the missing definitions, resolving the selected
                        // fields against `rootTypeName`.
                        def modObjDefs(rootTypeName: String): List[Stat] => List[Stat] =
                          scala.Function.chain(
                            List(
                              addVariableDefsTypeAlias(explicitVariableDefs, variableDefs),
                              addImports(schemaType.value),
                              addData(
                                schema,
                                operation,
                                config,
                                subquery.subqueries,
                                fragments,
                                schema.types.find(_.name == rootTypeName)
                              ),
                              addDataDecoder,
                              addConvenienceMethod(schemaType, operation, objName, config)
                            )
                          )

                        // A single object is emitted, so every root type must generate the same one.
                        val bodies: List[(String, List[Stat])] =
                          rootTypes.map(rootTypeName =>
                            rootTypeName -> modObjDefs(rootTypeName)(stats)
                          )

                        val newMods = GraphQLAnnotation.removeFrom(mods).filterNot {
                          case Mod.Abstract() => true
                          case _              => false
                        }

                        generatedMismatch(bodies).fold(
                          // Congratulations! You got a full-fledged GraphQLOperation (hopefully).
                          diagnostics + Patch.replaceTree(
                            obj,
                            indented(obj)(
                              q"..$newMods object ${Term
                                  .Name(
                                    objName
                                  )} ${buildTemplate(early, inits, (self.some, bodies.head._2))}".toString
                            )
                          ) + Patch.removeGlobalImport(GraphQLAnnotation.symbol)
                        )(mismatch => diagnostics + errorDiagnostic(mismatch, obj.pos))
                      }
                    }
                }
            }
          // Hand-written operations/subqueries: the generator is not used (no @GraphQL annotation),
          // but the document/subquery is still validated against the schema. Annotated definitions
          // are handled by the cases above, so they are excluded here to avoid validating twice.
          // Covers `GraphQLOperation[S]`/`GraphQLOperation.Typed[S, ...]` and the `GraphQLSubquery`
          // equivalents.
          case obj @ GraphQLOperationDefn(mods, inits, stats)
              if !hasGraphQLAnnotation(mods) &&
                (isGraphQLOperationDefn(inits, stats) || isGraphQLSubqueryDefn(inits, stats)) =>
            validateGraphQLDefn(mods, inits, stats, obj.pos, generating = false)
        }

    (importPatch ++ genPatch).sequence
      .unsafeRunSync()
      .asPatch
  }

}
