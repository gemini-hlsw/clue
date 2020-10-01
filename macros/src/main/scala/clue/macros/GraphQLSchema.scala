// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.macros

import scala.annotation.StaticAnnotation
import scala.reflect.macros.blackbox
import edu.gemini.grackle._
import cats.effect.IO
import cats.syntax.all._

class GraphQLSchema(
  val mappings: Map[String, String] = Map.empty,
  val eq:       Boolean = false,
  val show:     Boolean = false,
  val lenses:   Boolean = false,
  val reuse:    Boolean = false,
  val debug:    Boolean = false
) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro GraphQLSchemaImpl.resolve
}

private[clue] final class GraphQLSchemaImpl(val c: blackbox.Context) extends GraphQLMacro {
  import c.universe._

  final def resolve(annottees: Tree*): Tree =
    macroResolve(annottees: _*)

  // Just make sure "object Scalars" exists.
  private[this] val addScalars: List[Tree] => List[Tree] =
    addModuleDefs("Scalars",
                  eq = false,
                  show = false,
                  reuse = false,
                  modStatements = _ :+ q"def ignoreUnusedImportScalars(): Unit = ()"
    )

  private[this] def addEnums(schema: Schema, params: GraphQLParams): List[Tree] => List[Tree] =
    addModuleDefs(
      "Enums",
      eq = false,
      show = false,
      reuse = false,
      modStatements = scala.Function.chain(
        List((parentBody: List[Tree]) =>
          q"def ignoreUnusedImportEnums(): Unit = ()" +: parentBody
        ) ++
          schema.types
            .collect { case EnumType(name, _, values) => Enum(name, values.map(_.name)) }
            .map(
              _.addToParentBody(params.eq,
                                params.show,
                                params.reuse,
                                encoder = true,
                                decoder = true
              )
            )
      )
    )

  private[this] def addInputs(schema: Schema, params: GraphQLParams): List[Tree] => List[Tree] =
    addModuleDefs(
      "Types",
      eq = false,
      show = false,
      reuse = false,
      // TODO Only generate imports if not already defined
      modStatements = scala.Function.chain(
        List((parentBody: List[Tree]) =>
          List(q"import Scalars._",
               q"ignoreUnusedImportScalars()",
               q"import Enums._",
               q"ignoreUnusedImportEnums()",
               q"def ignoreUnusedImportTypes(): Unit = ()"
          ) ++ parentBody
        ) ++
          schema.types
            .collect { case InputObjectType(name, _, fields) =>
              CaseClass(
                name.capitalize,
                fields.map(iv => ClassParam.fromGrackleType(iv.name, iv.tpe, params.mappings))
              )
            }
            .map(
              _.addToParentBody(params.eq,
                                params.show,
                                params.lenses,
                                reuse = false,
                                encoder = true
              )
            )
      )
    )

  protected[this] def expand(annottees: Tree*): IO[Tree] =
    annottees match {
      case List(
            q"$objMods object $objName extends { ..$objEarlyDefs } with ..$objParents { $objSelf => ..$objDefs }"
          ) =>
        // Get macro settings passed thru -Xmacro-settings.
        val settings       = MacroSettings.fromCtxSettings(c.settings)
        // Get annotation parameters.
        val optionalParams = buildOptionalParams[GraphQLOptionalParams]
        val params         = optionalParams.resolve(settings)

        val typeName = objName.toTypeName

        val TermName(schemaName) = objName
        retrieveSchema(settings.schemaDirs, schemaName)
          .map { schema =>
            val modObjDefs = scala.Function.chain(
              List(addScalars, addEnums(schema, params), addInputs(schema, params))
            )

            // Congratulations! You got a full-fledged schema (hopefully).
            // We could use a phantom type instead of a sealed trait, but that wouldn't compile outside of an enclosing scope.
            q"""
              sealed trait $typeName
              $objMods object $objName extends { ..$objEarlyDefs } with ..$objParents { $objSelf =>
                ..${modObjDefs(objDefs)}
              }
            """
          }
          .flatTap(result => IO.whenA(params.debug)(log(result)))
    }

}
