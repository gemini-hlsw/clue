// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.gen

import grackle.EnumType
import grackle.InputObjectType
import grackle.Schema

import scala.meta.*

trait SchemaGen extends Generator {
  // Just make sure "object Scalars" exists.
  protected val addScalars: List[Stat] => List[Stat] =
    addModuleDefs(
      "Scalars",
      catsEq = false,
      catsShow = false,
      scalaJsReactReuse = false,
      bodyMod = _ :+ q"def ignoreUnusedImportScalars(): Unit = ()"
    )

  protected def addEnums(schema: Schema, config: GraphQLGenConfig): List[Stat] => List[Stat] =
    addModuleDefs(
      "Enums",
      catsEq = false,
      catsShow = false,
      scalaJsReactReuse = false,
      bodyMod = scala.Function
        .chain(
          List((parentBody: List[Stat]) =>
            q"def ignoreUnusedImportEnums(): Unit = ()" +: parentBody
          ) ++
            schema.types
              .collect { case EnumType(name, _, values, _) =>
                Enum(name, values.map(v => (v.name, Deprecation.fromDirectives(v.directives))))
              }
              .map(
                _.addToParentBody(
                  config.catsEq,
                  config.catsShow,
                  config.scalaJsReactReuse,
                  circeEncoder = true,
                  circeDecoder = true
                )
              )
        )
    )

  protected def addInputs(schema: Schema, config: GraphQLGenConfig): List[Stat] => List[Stat] =
    addModuleDefs(
      "Types",
      catsEq = false,
      catsShow = false,
      scalaJsReactReuse = false,
      // TODO Only generate imports if not already defined. Use "RemoveUnused" instead?
      bodyMod = scala.Function.chain(
        List((parentBody: List[Stat]) =>
          List(q"import Scalars._",
               q"ignoreUnusedImportScalars()",
               q"import Enums._",
               q"ignoreUnusedImportEnums()",
               q"def ignoreUnusedImportTypes(): Unit = ()"
          ) ++ parentBody
        ) ++
          schema.types
            .collect { case InputObjectType(name, _, fields, _) =>
              CaseClass(
                name.capitalize,
                fields.map(iv => ClassParam.fromGrackleType(iv.name, iv.tpe, isInput = true))
              )
            }
            .map(
              _.addToParentBody(
                config.catsEq,
                config.catsShow,
                config.monocleLenses,
                scalaJsReactReuse = false,
                circeEncoder = true
              )
            )
      )
    )
}
