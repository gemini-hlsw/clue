// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.gen

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import edu.gemini.grackle.QueryParser
import metaconfig.Configured
import scalafix.v1._

import scala.meta._

class GraphQLGen(config: GraphQLGenConfig)
    extends SemanticRule("GraphQLGen")
    with SchemaGen
    with QueryGen {
  def this() = this(GraphQLGenConfig())

  override def withConfiguration(config: Configuration): Configured[Rule] =
    config.conf
      .getOrElse("GraphQLGen")(this.config)
      .map(newConfig => new GraphQLGen(newConfig))

  private def indented(asTree: Tree)(lines: String): String = {
    val indentCols    = asTree.pos match {
      case range: Position.Range => range.startColumn
      case _                     => 0
    }
    val newLineIndent = "\n" + " " * indentCols
    newLineIndent + lines.replaceAll("\\n", newLineIndent)
  }

  override def fix(implicit doc: SemanticDocument): Patch = {
    val importPatch: List[IO[Patch]] =
      doc.tokens.collect {
        case token @ Token.Comment(value) if value.trim.startsWith("gql:") =>
          IO.pure(Patch.replaceToken(token, value.trim.stripPrefix("gql:").trim))
      }.toList

    val genPatch: List[IO[Patch]] =
      doc.tree
        .collect {
          case obj @ Defn.Object(
                GraphQLAnnotation(_),
                name,
                template
              ) => // Annotated objects are copied as-is
            // TODO: We should be able to validate the query!
            IO.pure(
              Patch.replaceTree(
                obj,
                indented(obj)(q"object $name $template".toString)
              ) + Patch.removeGlobalImport(GraphQLAnnotation.symbol)
            )
          case obj @ Defn.Object(
                GraphQLStubAnnotation(_),
                _,
                _
              ) =>
            IO.pure(Patch.replaceTree(obj, ""))
          case obj @ Defn.Trait.After_4_6_0(
                mods @ GraphQLSchemaAnnotation(_),
                templateName,
                Nil,
                _,
                Template.After_4_4_0(early, inits, self, stats, _)
              ) =>
            val objName = templateName.value
            config.getSchema(objName).map { schema =>
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
                    q"..$newMods object ${Term.Name(objName)} extends {..$early} with ..$inits { $self => ..${modObjDefs(stats)} }".toString
                  ).mkString("\n")
                )
              ) + Patch.removeGlobalImport(GraphQLSchemaAnnotation.symbol)
            }
          case obj @ Defn.Trait.After_4_6_0(
                mods @ GraphQLAnnotation(_),
                templateName,
                Nil,
                _,
                Template.After_4_4_0(early, inits, self, stats, _)
              ) if inits.exists {
                case Init.After_4_6_0(
                      Type.Apply.After_4_6_0(Type.Name("GraphQLOperation"), _),
                      _,
                      _
                    ) =>
                  true
                case _ => false
              } =>
            val objName = templateName.value

            extractSchemaType(inits) match {
              case None             =>
                abort(
                  "Invalid annotation target: must be a trait extending GraphQLOperation[Schema]"
                )
              case Some(schemaType) =>
                extractDocument(stats) match {
                  case None           =>
                    abort(
                      "The GraphQLOperation must define a 'val document: String' with a literal value."
                    )
                  case Some(document) =>
                    config.getSchema(schemaType.value).flatMap { schema =>
                      // Parse the operation.
                      val queryResult = QueryParser.parseText(document.render)
                      if (queryResult.isLeft)
                        abort(
                          s"Could not parse document: ${queryResult.left.get.toChain.map(_.toString).toList.mkString("\n")}"
                        )
                      else {
                        IO.whenA(queryResult.isBoth)(
                          log(
                            s"Warning parsing document: ${queryResult.left.get.toChain.map(_.toString).toList.mkString("\n")}"
                          )
                        ) >> IO {
                          val operation = queryResult.toOption.get

                          // Modifications to add the missing definitions.
                          val modObjDefs = scala.Function.chain(
                            List(
                              addImports(schemaType.value),
                              addVars(schema, operation, config),
                              addData(schema, operation, config, document.subqueries),
                              addVarEncoder,
                              addDataDecoder,
                              addConvenienceMethod(schemaType, operation, objName)
                            )
                          )

                          val newMods = GraphQLAnnotation.removeFrom(mods)

                          // Congratulations! You got a full-fledged GraphQLOperation (hopefully).
                          Patch.replaceTree(
                            obj,
                            indented(obj)(
                              List(
                                q"..$newMods object ${Term
                                    .Name(objName)} extends {..$early} with ..$inits { $self => ..${modObjDefs(stats)} }".toString
                              ).mkString("\n")
                            )
                          ) + Patch.removeGlobalImport(GraphQLAnnotation.symbol)
                        }
                      }
                    }
                }
            }
          case obj @ Defn.Class.After_4_6_0(
                mods @ GraphQLAnnotation(_),
                templateName,
                Nil,
                _,
                Template.After_4_4_0(early, inits, self, stats, _)
              ) if inits.exists {
                case Init.After_4_6_0(
                      Type.Apply.After_4_6_0(Type.Name("GraphQLSubquery"), _),
                      _,
                      _
                    ) =>
                  true
                case _ => false
              } =>
            val objName = templateName.value

            extractSchemaAndRootTypes(inits) match {
              case None                             =>
                abort(
                  "Invalid annotation target: must be a trait extending GraphQLOperation[Schema](rootType) where 'rootType` is a literal String value."
                )
              case Some((schemaType, rootTypeName)) =>
                extractSubquery(stats) match {
                  case None           =>
                    abort(
                      "The GraphQLOperation must define a 'val subquery' with a literal String value."
                    )
                  case Some(subquery) =>
                    config.getSchema(schemaType.value).flatMap { schema =>
                      // Parse the operation.
                      val queryResult = QueryParser.parseText(s"query ${subquery.render}")
                      if (queryResult.isLeft)
                        abort(
                          s"Could not parse document: ${queryResult.left.get.toChain.map(_.toString).toList.mkString("\n")}"
                        )
                      else {
                        IO.whenA(queryResult.isBoth)(
                          log(
                            s"Warning parsing document: ${queryResult.left.get.toChain.map(_.toString).toList.mkString("\n")}"
                          )
                        ) >> IO {
                          val operation = queryResult.toOption.get

                          // Modifications to add the missing definitions.
                          val modObjDefs = scala.Function.chain(
                            List(
                              addImports(schemaType.value),
                              addData(
                                schema,
                                operation,
                                config,
                                subquery.subqueries,
                                schema.types.find(_.name == rootTypeName)
                              ),
                              addDataDecoder,
                              addConvenienceMethod(schemaType, operation, objName)
                            )
                          )

                          val newMods = GraphQLAnnotation.removeFrom(mods).filterNot {
                            case Mod.Abstract() => true
                            case _              => false
                          }

                          // Congratulations! You got a full-fledged GraphQLOperation (hopefully).
                          Patch.replaceTree(
                            obj,
                            indented(obj)(
                              List(
                                q"..$newMods object ${Term
                                    .Name(objName)} extends {..$early} with ..$inits { $self => ..${modObjDefs(stats)} }".toString
                              ).mkString("\n")
                            )
                          ) + Patch.removeGlobalImport(GraphQLAnnotation.symbol)
                        }
                      }
                    }
                }
            }
        }

    (importPatch ++ genPatch).sequence
      .unsafeRunSync()
      .asPatch
  }

}
