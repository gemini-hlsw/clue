package clue.macros

import scala.annotation.StaticAnnotation
import scala.reflect.macros.blackbox
import edu.gemini.grackle._

class GraphQLSchema(
  val mappings: Map[String, String] = Map.empty,
  val eq:       Boolean = false,
  val show:     Boolean = false,
  val lenses:   Boolean = false,
  val reuse:    Boolean = false,
  val debug:    Boolean = false
) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro GraphQLSchemaImpl.expand
}

private[clue] final class GraphQLSchemaImpl(val c: blackbox.Context) extends GrackleMacro {
  import c.universe._

  final def expand(annottees: Tree*): Tree =
    annottees match {
      case List(
            q"$objMods object $objName extends { ..$objEarlyDefs } with ..$objParents { $objSelf => ..$objDefs }"
          ) =>
        // Get macro settings passed thru -Xmacro-settings.
        val settings       = MacroSettings.fromCtxSettings(c.settings)
        // Get annotation parameters.
        val optionalParams = buildOptionalParams[GraphQLOptionalParams]
        val params         = optionalParams.resolve(settings)

        val TermName(schemaName) = objName
        val schema               = retrieveSchema(settings.schemaDirs, schemaName)

        // Actually define a sum type here.
        val enums    = schema.types.collect { case EnumType(name, _, values) =>
          Enum(name.capitalize, values.map(_.name.capitalize))
        }
        val enumDefs = enums.flatMap(
          _.toTree(
            params.eq,
            params.show,
            params.reuse,
            encoder = true,
            decoder = true
          )
        )

        val inputClasses = schema.types
          .collect { case InputObjectType(name, _, fields) =>
            CaseClass(name.capitalize,
                      fields.map(iv => ClassParam.fromGrackleType(iv.name, iv.tpe, params.mappings))
            )
          }
        val inputDefs    =
          inputClasses.flatMap(
            _.toTree(params.eq, params.show, params.lenses, params.reuse, encoder = true)
          )

        // Congratulations! You got a full-fledged schema (hopefully).
        val result =
          q"""
            $objMods object $objName extends { ..$objEarlyDefs } with ..$objParents { $objSelf =>
              ..$objDefs

              trait Enums {
                ..$enumDefs
              }
              object Enums extends Enums

              trait Types extends Scalars with Enums {
                ..$inputDefs
              }
              object Types extends Types
            }
          """

        if (params.debug) log(result)

        result
    }
}
