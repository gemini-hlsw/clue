package clue.macros

import scala.annotation.StaticAnnotation
import scala.reflect.macros.blackbox
import edu.gemini.grackle._

class GraphQLSchema extends StaticAnnotation {
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
        val settings = MacroSettings.fromCtxSettings(c.settings)

        val TermName(schemaName) = objName
        val schema               = retrieveSchema(settings.schemaDirs, schemaName)

        // Actually define a sum type here.
        val enums    = schema.types.collect { case EnumType(name, _, _) =>
          CaseClass(name.capitalize, List.empty)
        }
        val enumDefs = enums.flatMap(
          _.toTree(Map.empty, true, true, true, false, encoder = true, decoder = true)
        )

        val inputClasses = schema.types
          .collect { case InputObjectType(name, _, fields) =>
            CaseClass(name.capitalize, fields.map(iv => ClassParam(iv.name, iv.tpe)))
          }
        val inputDefs    =
          inputClasses.flatMap(_.toTree(Map.empty, true, true, true, false, encoder = true))

        // Congratulations! You got a full-fledged schema (hopefully).
        val result =
          q"""
            $objMods object $objName extends { ..$objEarlyDefs } with ..$objParents { $objSelf =>
              ..$objDefs

              ..$enumDefs
              ..$inputDefs
            }
          """

        // if (params.debug)
        // log(result)

        result
    }
}
