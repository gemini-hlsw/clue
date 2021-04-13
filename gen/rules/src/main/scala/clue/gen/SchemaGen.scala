// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.gen

import scala.meta._
import edu.gemini.grackle.Schema
import edu.gemini.grackle.EnumType
import edu.gemini.grackle.InputObjectType

trait SchemaGen extends Generator {
  // Just make sure "object Scalars" exists.
  protected val addScalars: List[Stat] => List[Stat] =
    addModuleDefs("Scalars",
                  catsEq = false,
                  catsShow = false,
                  scalaJSReactReuse = false,
                  bodyMod = _ :+ q"def ignoreUnusedImportScalars(): Unit = ()"
    )

  protected def addEnums(schema: Schema, config: GraphQLGenConfig): List[Stat] => List[Stat] =
    addModuleDefs(
      "Enums",
      catsEq = false,
      catsShow = false,
      scalaJSReactReuse = false,
      bodyMod = scala.Function
        .chain(
          List((parentBody: List[Stat]) =>
            q"def ignoreUnusedImportEnums(): Unit = ()" +: parentBody
          ) ++
            schema.types
              .collect { case EnumType(name, _, values) => Enum(name, values.map(_.name)) }
              .map(
                _.addToParentBody(config.catsEq,
                                  config.catsShow,
                                  config.scalaJSReactReuse,
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
      scalaJSReactReuse = false,
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
            .collect { case InputObjectType(name, _, fields) =>
              CaseClass(
                name.capitalize,
                fields.map(iv => ClassParam.fromGrackleType(iv.name, iv.tpe, isInput = true))
              )
            }
            .map(
              _.addToParentBody(config.catsEq,
                                config.catsShow,
                                config.monocleLenses,
                                scalaJSReactReuse = false,
                                circeEncoder = true
              )
            )
      )
    )
}
