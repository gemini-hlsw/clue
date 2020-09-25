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

  private[this] val addScalars: List[Tree] => List[Tree] =
    addTraitStatements("Scalars", List.empty).andThen(
      addModuleDefs("Scalars", eq = false, show = false, reuse = false)
    )

  private[this] def addEnums(schema: Schema, params: GraphQLParams): List[Tree] => List[Tree] =
    modifyTraitStatements(
      "Enums",
      scala.Function.chain(
        schema.types
          .collect { case EnumType(name, _, values) => Enum(name, values.map(_.name)) }
          .map(
            _.addToParentBody(params.eq, params.show, params.reuse, encoder = true, decoder = true)
          )
      )
    ).andThen(addModuleDefs("Enums", eq = false, show = false, reuse = false))

  private[this] def addInputs(schema: Schema, params: GraphQLParams): List[Tree] => List[Tree] =
    modifyTraitStatements(
      "Types",
      scala.Function.chain(
        schema.types
          .collect { case InputObjectType(name, _, fields) =>
            CaseClass(name.capitalize,
                      fields.map(iv => ClassParam.fromGrackleType(iv.name, iv.tpe, params.mappings))
            )
          }
          .map(
            _.addToParentBody(params.eq, params.show, params.lenses, reuse = false, encoder = true)
          )
      )
    ).andThen(addModuleDefs("Types", eq = false, show = false, reuse = false))

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

        val typeName = objName.toTypeName

        val TermName(schemaName) = objName
        val schema               = retrieveSchema(settings.schemaDirs, schemaName)

        val modObjDefs = scala.Function.chain(
          List(addScalars, addEnums(schema, params), addInputs(schema, params))
        )

        // Congratulations! You got a full-fledged schema (hopefully).
        val result =
          // We could use a phantom type instead of a sealed trait, but that wouldn't compile outside of an enclosing scope.
          q"""
            sealed trait $typeName
            $objMods object $objName extends { ..$objEarlyDefs } with ..$objParents { $objSelf =>
              ..${modObjDefs(objDefs)}
            }
          """

        if (params.debug) log(result)

        result
    }
}
