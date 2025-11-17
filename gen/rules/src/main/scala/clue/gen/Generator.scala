// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.gen

// import scalafix.v1._
import cats.syntax.all.*
import grackle.ScalarType
import grackle.SchemaRenderer
import grackle.Type as GType

import java.util.regex.Pattern
import scala.meta.*

trait Generator {
  protected val TypeSelect = "__typename"

  protected val DefaultMappings: Map[String, Type] =
    Map("ID" -> t"String", "uuid" -> t"java.util.UUID")

  protected val MetaTypes: Map[String, GType] =
    // "__schema" | "__type"
    Map(TypeSelect -> ScalarType("String", "Type Discriminator".some, List.empty))

  protected sealed trait DefineType
  protected object DefineType {
    case object Skip extends DefineType
    case class Define(newParentBody: List[Stat], early: List[Stat], inits: List[Init])
        extends DefineType
  }
  import DefineType._

  protected def mustDefineType(typeName: String): List[Stat] => DefineType =
    parentBody => {
      val (extensionDefinitions, newParentBody) =
        parentBody.partitionMap {
          // q"..$mods trait $tname[..$tparams] extends $template"
          case Defn.Trait.Initial(_, tname, _, _, Template.Initial(early, inits, _, _))
              if tname.value == typeName =>
            (early, inits).asLeft
          case other =>
            other.asRight
        }
      extensionDefinitions.headOption match {
        case None                 =>
          parentBody
            .collectFirst {
              // q"..$mods type $tname[..$tparams] = $tpe"
              case Defn.Type.Initial(_, tname, _, _) if tname.value == typeName     => Skip
              // q"..$mods class $tname[..$tparams] ..$ctorMods (...$paramss) extends $template"
              case Defn.Class.Initial(_, tname, _, _, _) if tname.value == typeName => Skip
            }
            .getOrElse(Define(parentBody, List.empty, List.empty))
        case Some((early, inits)) =>
          Define(newParentBody, early, inits)
      }
    }

  private def nestedTypeTree(nestTree: Term.Ref, tpe: Type): Type =
    tpe match {
      case named @ Type.Name(_)           => Type.Select(nestTree, named)
      // case Type.Select?
      case Type.Apply.Initial(ttpe, args) =>
        Type.Apply.Initial(ttpe, args.map(t => nestedTypeTree(nestTree, t)))
      case other                          =>
        throw new Exception(s"Type structure [$other] not supported.")
    }

  private def qualifiedNestedType(nestTree: Option[Term.Ref], tpe: Type): Type =
    nestTree.fold(tpe) { tree =>
      nestedTypeTree(tree, tpe)
    }

  protected case class Deprecation(reason: String)
  object Deprecation {
    def fromDirectives(directives: List[grackle.Directive]): Option[Deprecation] =
      directives.find(_.name == "deprecated").map { d =>
        Deprecation(
          d.args
            .find(_.name == "reason")
            .map(binding =>
              SchemaRenderer
                .renderValue(binding.value) // unquote
                .replaceFirst("^\"", "")
                .replaceFirst("\"$", "")
            )
            .getOrElse("No longer supported")
        )
      }
  }

  /**
   * Represents a parameter that will be used for a generated case class or variable.
   *
   * Consists of the name of the parameter and its Grackle type.
   */
  protected case class ClassParam(
    name:        String,
    tpe:         Type,
    overrides:   Boolean = false,
    deprecation: Option[Deprecation] = None
  ) {

    def typeTree(nestTree: Option[Term.Ref], nestedTypes: Map[String, Term.Ref]): Type =
      nestTree match {
        case None => tpe
        case _    => qualifiedNestedType(nestedTypes.get(name), tpe)
      }

    def toTree(
      nestTree:    Option[Term.Ref],
      nestedTypes: Map[String, Term.Ref],
      asVals:      Boolean = false
    ): Term.Param = {
      val n: Term.Name    = Term.Name(name)
      val t: Type         = typeTree(nestTree, nestedTypes)
      val d: Option[Term] = tpe match {
        // case t"Option[$_]"         =>
        case Type.Apply.Initial(Type.Name("Option"), _) =>
          if (!asVals) q"None".some else none
        // case t"clue.data.Input[_]"              =>
        case Type.Apply.Initial(
              Type.Select(
                Term.Select(Term.Name("clue"), Term.Name("data")),
                Type.Name("Input")
              ),
              _
            ) =>
          if (!asVals) q"clue.data.Ignore".some else none
        case _                                          => none
      }
      val mods: List[Mod] =
        deprecation.fold(List.empty[Mod])(dep => List(mod"@deprecated(${dep.reason})")) ++
          (if (!asVals && overrides) List(mod"override") else List.empty)
      param"..$mods val $n: $t = $d"
    }
  }

  protected object ClassParam {
    def fromGrackleType(
      name:         String,
      tpe:          grackle.Type,
      isInput:      Boolean,
      alias:        Option[String] = None,
      typeOverride: Option[Type] = None,
      deprecation:  Option[Deprecation] = None
    ): ClassParam = {
      def resolveType(tpe: grackle.Type): Type =
        tpe match {
          case grackle.NullableType(tpe) =>
            if (isInput)
              t"clue.data.Input[${resolveType(tpe)}]"
            else
              t"Option[${resolveType(tpe)}]"
          case grackle.ListType(tpe)     => t"List[${resolveType(tpe)}]"
          case nt: grackle.NamedType     =>
            DefaultMappings.getOrElse(
              nt.name,
              typeOverride.getOrElse(Type.Name(snakeToCamel(alias.getOrElse(nt.name))))
            )
        }

      ClassParam(name, resolveType(tpe), deprecation = deprecation)
    }
  }

  protected sealed trait Class {
    val name: String

    protected val camelName = snakeToCamel(name)

    protected def nextNestPath(nestPath: Option[Term]): Option[Term.Ref] =
      nestPath
        .fold[Term.Ref](Term.Name(camelName))(t => Term.Select(t, Term.Name(camelName)))
        .some

    protected def nextNestedTypes(
      nested:      List[Class],
      nestPath:    Option[Term],
      nestedTypes: Map[String, Term.Ref]
    ): Map[String, Term.Ref] =
      nestedTypes ++ nested.flatMap(c => nextNestPath(nestPath).map(path => c.name -> path))

    def addToParentBody(
      catsEq:            Boolean,
      catsShow:          Boolean,
      monocleLenses:     Boolean,
      scalaJsReactReuse: Boolean,
      circeEncoder:      Boolean = false,
      circeDecoder:      Boolean = false,
      forceModule:       Boolean = false,
      nestPath:          Option[Term.Ref] = None,
      nestedTypes:       Map[String, Term.Ref] = Map.empty,
      extending:         Option[String] = None
    ): List[Stat] => List[Stat]
  }

  protected[this] def buildTemplate(
    early: List[Stat],
    inits: List[Init],
    body:  Option[(Option[Self], List[Stat])] = None
  ): Template =
    (early, body) match {
      case (Nil, None)                     =>
        template"..$inits"
      case (Nil, Some((None, defs)))       =>
        template"..$inits { ..$defs }"
      case (Nil, Some((self, defs)))       =>
        template"..$inits { $self => ..$defs }"
      case (someEarly, None)               =>
        template"{..$someEarly} with ..$inits"
      case (someEarly, Some((None, defs))) =>
        template"{..$someEarly} with ..$inits { ..$defs }"
      case (someEarly, Some((self, defs))) =>
        template"{..$someEarly} with ..$inits { $self => ..$defs }"
    }

  protected[this] def buildTemplate(
    early: List[Stat],
    inits: List[Init],
    body:  (Option[Self], List[Stat])
  ): Template =
    buildTemplate(early, inits, body.some)

  /**
   * Compute a case class definition.
   */
  protected[this] def addCaseClassDef(
    name:      String,
    pars:      List[Term.Param],
    extending: Option[String] = None
  ): List[Stat] => (List[Stat], Boolean) =
    parentBody =>
      mustDefineType(name)(parentBody) match {
        case Skip                                =>
          (parentBody, false)
        case Define(newParentBody, early, inits) =>
          val allInits = inits ++ extending.map(t => init"${Type.Name(t)}()")

          (newParentBody :+
             q"case class ${Type.Name(name)}(..$pars) ${buildTemplate(early, allInits)}",
           true
          )
      }

  /**
   * The definition of a case class to contain an object from the query response.
   *
   * Consists of the class name and its [[ClassParam]] parameters.
   */
  protected case class CaseClass(
    name:   String,
    params: List[ClassParam],
    nested: List[Class] = List.empty
  ) extends Class {

    def addToParentBody(
      catsEq:            Boolean,
      catsShow:          Boolean,
      monocleLenses:     Boolean,
      scalaJsReactReuse: Boolean,
      circeEncoder:      Boolean = false,
      circeDecoder:      Boolean = false,
      forceModule:       Boolean = false,
      nestPath:          Option[Term.Ref] = None,
      nestedTypes:       Map[String, Term.Ref] = Map.empty,
      extending:         Option[String] = None
    ): List[Stat] => List[Stat] =
      parentBody => {
        val nextPath: Option[Term.Ref]       = nextNestPath(nestPath)
        val nextTypes: Map[String, Term.Ref] = nextNestedTypes(nested, nestPath, nestedTypes)

        val (newBody, wasMissing) =
          addCaseClassDef(camelName, params.map(_.toTree(nextPath, nextTypes)), extending)(
            parentBody
          )

        val addNested = nested.map(
          _.addToParentBody(
            catsEq,
            catsShow,
            monocleLenses,
            scalaJsReactReuse,
            circeEncoder,
            circeDecoder,
            nestPath = nextPath
          )
        )

        val addLenses = Option.when(monocleLenses) { (moduleBody: List[Stat]) =>
          val lensesDef = params.map { param =>
            val thisType  = qualifiedNestedType(nestPath, Type.Name(camelName))
            val childType = param.typeTree(nextPath, nextTypes)
            q"val ${Pat.Var(Term.Name(param.name))}:  monocle.Lens[$thisType, $childType] = monocle.macros.GenLens[$thisType](_.${Term
                .Name(param.name)})"
          // q"val ${Term.Name(param.name)}: monocle.Lens[$thisType, $childType] = monocle.macros.GenLens[$thisType](_.${Term.Name(param.name)})"
          }
          moduleBody ++ lensesDef
        }

        if (wasMissing || forceModule)
          addModuleDefs(
            camelName,
            catsEq,
            catsShow,
            scalaJsReactReuse,
            circeEncoder,
            circeDecoder,
            bodyMod = scala.Function.chain(addNested ++ addLenses),
            nestPath = nestPath
          )(newBody)
        else
          newBody
      }
  }

  protected case class Sum(
    params:    List[ClassParam],
    nested:    List[Class] = List.empty,
    instances: List[CaseClass]
  )

  protected def paramToVal(param: Term.Param): Stat =
    param.default.fold[Stat](
      q"val ${Term.Name(param.name.value)}: ${param.decltpe.get}"
    )(d => q"val ${Pat.Var(Term.Name(param.name.value))}: ${param.decltpe.get} = $d")

  protected def addSumTrait(
    name:      String,
    pars:      List[Term.Param],
    extending: Option[String] = None
  ): List[Stat] => (List[Stat], Boolean) = { parentBody =>
    mustDefineType(name)(parentBody) match {
      case Skip                                =>
        (parentBody, false)
      case Define(newParentBody, early, inits) =>
        val allInits = inits ++ extending.map(t => init"${Type.Name(t)}()")

        (newParentBody :+
           q"sealed trait ${Type.Name(
               name
             )} ${buildTemplate(early, allInits, (none, pars.map(paramToVal)))}",
         true
        )
    }
  }

  protected case class SumClass(
    name: String,
    sum:  Sum
  ) extends Class {

    override def addToParentBody(
      catsEq:            Boolean,
      catsShow:          Boolean,
      monocleLenses:     Boolean,
      scalaJsReactReuse: Boolean,
      circeEncoder:      Boolean,
      circeDecoder:      Boolean,
      forceModule:       Boolean,
      nestPath:          Option[Term.Ref] = None,
      nestedTypes:       Map[String, Term.Ref] = Map.empty,
      extending:         Option[String] = None
    ): List[Stat] => List[Stat] =
      parentBody => {
        val nextPath              = nextNestPath(nestPath)
        val nextTypes             = nextNestedTypes(sum.nested, nestPath, nestedTypes)
        val (newBody, wasMissing) =
          addSumTrait(
            camelName,
            sum.params.map(_.toTree(nextPath, nextTypes, asVals = true)),
            extending
          )(
            parentBody
          )

        val addDefinitions =
          sum.nested.map(
            _.addToParentBody(
              catsEq,
              catsShow,
              monocleLenses,
              scalaJsReactReuse,
              circeEncoder,
              circeDecoder,
              forceModule,
              nextPath,
              nextTypes
            )
          ) ++
            sum.instances.map(
              _.addToParentBody(
                catsEq,
                catsShow,
                monocleLenses,
                scalaJsReactReuse,
                circeEncoder,
                circeDecoder,
                forceModule,
                nextPath,
                nextTypes,
                camelName.some // Extends
              )
            )

        val addLenses = Option.when(monocleLenses) { (moduleBody: List[Stat]) =>
          val lensesDef = sum.params.map { param =>
            val paramNameDef = Term.Name(param.name)
            val thisType     = qualifiedNestedType(nestPath, Type.Name(camelName))
            val childType    = param.typeTree(nextPath, nextTypes)
            val cases        = sum.instances.map { cc =>
              val caseType = qualifiedNestedType(nextPath, Type.Name(cc.name))
              p"case s: $caseType => s.copy($paramNameDef = v)"
            }
            q"""val ${Pat.Var(paramNameDef)}: monocle.Lens[$thisType, $childType] = 
                  monocle.Lens[$thisType, $childType](_.$paramNameDef){ v =>
                    _ match {..case $cases}
                  }"""
          }
          moduleBody ++ lensesDef
        }

        if (wasMissing || forceModule)
          addModuleDefs(
            camelName,
            catsEq,
            catsShow,
            scalaJsReactReuse,
            circeEncoder,
            circeDecoder,
            TypeType.Sum(sum.instances.map(_.name)),
            bodyMod = scala.Function.chain(addDefinitions ++ addLenses),
            nestPath = nestPath
          )(newBody)
        else
          newBody
      }
  }

  protected case class Enum(name: String, values: List[(String, Option[Deprecation])]) {
    def addToParentBody(
      catsEq:            Boolean,
      catsShow:          Boolean,
      scalaJsReactReuse: Boolean,
      circeEncoder:      Boolean = false,
      circeDecoder:      Boolean = false
    ): List[Stat] => List[Stat] =
      addEnum(
        snakeToCamel(name),
        values,
        catsEq,
        catsShow,
        scalaJsReactReuse,
        circeEncoder,
        circeDecoder
      )
  }

  protected def addEnum(
    name:              String,
    values:            List[(String, Option[Deprecation])],
    catsEq:            Boolean,
    catsShow:          Boolean,
    scalaJsReactReuse: Boolean,
    circeEncoder:      Boolean = false,
    circeDecoder:      Boolean = false
  ): List[Stat] => List[Stat] =
    parentBody =>
      mustDefineType(name)(parentBody) match {
        case Skip                                =>
          parentBody
        case Define(newParentBody, early, inits) =>
          val allInits   = inits :+ init"${Type.Name(name)}()"
          val enumValues = values.map(v => EnumValue.fromStringAndDeprecation(v._1, v._2))
          addModuleDefs(
            name,
            catsEq,
            catsShow,
            scalaJsReactReuse,
            circeEncoder,
            circeDecoder,
            TypeType.Enum(enumValues),
            _ ++ enumValues.map { enumValue =>
              val mods: List[Mod] = enumValue.deprecation.fold(List.empty[Mod])(dep =>
                List(mod"@deprecated(${dep.reason})")
              )
              q"..$mods case object ${Term.Name(enumValue.className)} ${buildTemplate(early, allInits)}"
            },
            ignoreDeprecation = enumValues.exists(_.deprecation.isDefined)
          )(
            newParentBody :+ q"sealed trait ${Type.Name(name)}"
          )
      }

  protected def snakeToCamel(s: String): String = {
    val wordPattern: Pattern = Pattern.compile("([a-zA-Z0-9]+)(?:_|$)")
    val unscream             = if (!s.exists(_.isLower)) s.toLowerCase else s
    val matcher              = wordPattern.matcher(unscream)
    val sb                   = new StringBuffer
    while (matcher.find())
      matcher.appendReplacement(sb, matcher.group(1).capitalize)
    matcher.appendTail(sb)
    sb.toString
  }

  protected case class EnumValue(
    asString:    String,
    className:   String,
    deprecation: Option[Deprecation]
  )
  protected object EnumValue {
    def fromStringAndDeprecation(asString: String, deprecation: Option[Deprecation]): EnumValue =
      EnumValue(asString, snakeToCamel(asString), deprecation)
  }

  protected sealed trait TypeType
  protected object TypeType {
    case object CaseClass                      extends TypeType
    case class Enum(values: List[EnumValue])   extends TypeType
    case class Sum(subtypeNames: List[String]) extends TypeType
  }

  protected def modifyModuleStatements(
    moduleName:        String,
    bodyMod:           List[Stat] => List[Stat],
    ignoreDeprecation: Boolean = false
  ): List[Stat] => List[Stat] =
    parentBody => {
      val (newStats, modified) =
        parentBody.foldLeft((List.empty[Stat], false)) { case ((newStats, modified), stat) =>
          stat match {
            // q"..$mods object $objName extends $template}"
            case Defn.Object(mods, objName, Template.Initial(early, inits, self, body))
                if objName.value == moduleName =>
              (
                newStats :+ q"..$mods object $objName ${buildTemplate(early,
                                                                      inits,
                                                                      (self.some, bodyMod(body))
                  )}",
                true
              )
            case other =>
              (newStats :+ other, modified)
          }
        }
      if (modified) newStats
      else {
        val mods: List[Mod] =
          if (ignoreDeprecation) List(mod"@scala.annotation.nowarn(${"cat=deprecation"})")
          else List.empty
        newStats :+ q"..$mods object ${Term.Name(moduleName)} { ..${bodyMod(List.empty)} }"
      }
    }

  /**
   * Compute a companion object with typeclasses.
   */
  protected def addModuleDefs(
    name:              String,
    catsEq:            Boolean,
    catsShow:          Boolean,
    scalaJsReactReuse: Boolean,
    circeEncoder:      Boolean = false,
    circeDecoder:      Boolean = false,
    typeType:          TypeType = TypeType.CaseClass,
    bodyMod:           List[Stat] => List[Stat] = identity,
    nestPath:          Option[Term.Ref] = None,
    ignoreDeprecation: Boolean = false
  ): List[Stat] => List[Stat] = {
    val n: Type.Ref =
      nestPath.fold[Type.Ref](Type.Name(name))(t => Type.Select(t, Type.Name(name)))

    def valName(prefix: String): Pat.Var = Pat.Var(Term.Name(s"$prefix$name"))

    val eqDef = Option.when(catsEq)(
      q"implicit val ${valName("eq")}: cats.Eq[$n] = cats.Eq.fromUniversalEquals"
    )

    val showDef = Option.when(catsShow)(
      q"implicit val ${valName("show")}: cats.Show[$n] = cats.Show.fromToString"
    )

    val reuseDef = Option.when(scalaJsReactReuse)(
      q"""implicit val ${valName("reuse")}: japgolly.scalajs.react.Reusability[$n] = {
              japgolly.scalajs.react.Reusability.derive
            }
          """
    )

    val encoderDef = Option.when(circeEncoder)(typeType match {
      case TypeType.CaseClass        =>
        q"implicit val ${valName("jsonEncoder")}: io.circe.Encoder.AsObject[$n] = io.circe.generic.semiauto.deriveEncoder[$n].mapJsonObject(clue.data.Input.dropIgnores)"
      case TypeType.Enum(enumValues) =>
        val cases: List[Case] =
          enumValues.map(enumValue =>
            p"case ${Term.Name(enumValue.className)} => ${enumValue.asString}"
          )
        q"implicit val ${valName("jsonEncoder")}: io.circe.Encoder[$n] = io.circe.Encoder.encodeString.contramap[$n]{..case $cases}"
      case TypeType.Sum(_)           =>
        q"implicit val ${valName("jsonEncoder")}: io.circe.Encoder.AsObject[$n] = io.circe.generic.semiauto.deriveEncoder[$n]"
    })

    val decoderDef = Option.when(circeDecoder)(typeType match {
      case TypeType.CaseClass         =>
        q"implicit val ${valName("jsonDecoder")}: io.circe.Decoder[$n] = io.circe.generic.semiauto.deriveDecoder[$n]"
      case TypeType.Enum(enumValues)  =>
        val cases: List[Case] =
          enumValues.map(enumValue =>
            p"case ${enumValue.asString} => Right(${Term.Name(enumValue.className)})"
          ) :+ p"""case other => Left(s"Invalid value [$$other]")"""
        q"implicit val ${valName("jsonDecoder")}: io.circe.Decoder[$n] = io.circe.Decoder.decodeString.emap(_ match {..case $cases})"
      case TypeType.Sum(subtypeNames) =>
        val baseTerm: Term.Ref =
          nestPath.fold[Term.Ref](Term.Name(name))(t => Term.Select(t, Term.Name(name)))

        def subtypeDecoder(subtypeName: String): Term = {
          val subType = Type.Select(baseTerm, Type.Name(subtypeName))
          q"io.circe.Decoder[$subType].asInstanceOf[io.circe.Decoder[$n]]"
        }

        q"""implicit val ${valName("jsonDecoder")}: io.circe.Decoder[$n] = 
               List[io.circe.Decoder[$n]](
                ..${subtypeNames.map(subtypeDecoder)}
               ).reduceLeft(_ or _)
        """
    })

    modifyModuleStatements(
      name,
      stats => bodyMod(stats) ++ List(eqDef, showDef, reuseDef, encoderDef, decoderDef).flatten,
      ignoreDeprecation
    )
  }
}
