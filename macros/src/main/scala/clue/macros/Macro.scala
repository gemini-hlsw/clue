// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.macros

import cats.syntax.all._
import scala.reflect.macros.blackbox
import scala.reflect.ClassTag
import cats.effect.IO
import java.util.regex.Pattern

protected[macros] trait Macro {
  val c: blackbox.Context

  import c.universe._

  @inline final def macroResolve(annottees: Tree*): Tree =
    expand(annottees: _*)
      .handleError(t =>
        c.abort(
          c.enclosingPosition,
          s"${t.getClass.getName}: ${t.getMessage}\n at ${t.getStackTrace.mkString("\n at")}"
        )
      )
      .unsafeRunSync()

  protected[this] def expand(annottees: Tree*): IO[Tree]

  protected[this] def abort(msg: String): IO[Nothing] =
    IO.raiseError(new Exception(msg))

  /**
   * Abort the macro showing an error message.
   */
  // protected[this] def abort(msg: Any): Nothing =
  //   c.abort(c.enclosingPosition, msg.toString)

  /**
   * Log debug info.
   */
  protected[this] def log(msg: Any): IO[Unit] =
    IO(c.info(c.enclosingPosition, msg.toString, force = true))

  /**
   * Log an actual Tree AST (not the Scala code equivalent).
   */
  protected[this] def debugTree(tree: Tree): IO[Unit] =
    log(c.universe.showRaw(tree))

  /**
   * Parse a type name (c.parse only parses terms).
   */
  protected[this] def parseType(tpe: String): Tree =
    c.parse(tpe) match {
      case Ident(TermName(name))        => Ident(TypeName(name))
      case Select(tree, TermName(name)) => Select(tree, TypeName(name))
      case other                        => // Let it crash (but tell us why)
        (debugTree(other) >> abort(s"Unexpected type [$tpe]")).unsafeRunSync()
    }

  protected[this] def snakeToCamel(s: String): String = {
    val wordPattern: Pattern = Pattern.compile("[a-zA-Z0-9]+(_|$)")
    val unscream             = if (!s.exists(_.isLower)) s.toLowerCase else s
    wordPattern.matcher(unscream).replaceAll(_.group.stripSuffix("_").capitalize)
  }

  /**
   * Extract the unqualified type name from a type.
   */
  protected[this] def unqualifiedType(tpe: Tree): Option[String] = tpe match {
    case Ident(TypeName(tpe))     => tpe.some
    case Select(_, TypeName(tpe)) => tpe.some
    case _                        => none
  }

  protected[this] def buildOptionalParams[T](implicit tag: ClassTag[T]): T = {
    val paramsClassName = parseType(tag.runtimeClass.getName)

    val tree =
      c.prefix.tree match {
        case q"new ${macroName}(..$params)" =>
          // Convert parameters to Some(...).
          val optionalParams = params.map {
            case value @ Literal(Constant(_)) =>
              Apply(Ident(TermName("Some")), List(value))
            case NamedArg(name, value)        =>
              NamedArg(name, Apply(Ident(TermName("Some")), List(value)))
          }

          q"new $paramsClassName(..$optionalParams)"
        case q"new ${macroName}"            => q"new $paramsClassName()"
      }

    c.eval(c.Expr[T](tree))
  }

  protected[this] object TypeNamesToTermNames extends Transformer {
    override def transform(tree: Tree): Tree = tree match {
      case Ident(name)             => Ident(name.toTermName)
      case Select(qualifier, name) => Select(super.transform(qualifier), name.toTermName)
      case _                       => super.transform(tree)
    }
  }

  protected[this] def isTypeDefined(typeName: String): List[Tree] => Boolean =
    parentBody => {
      val tpe = TypeName(typeName)
      parentBody.exists {
        case q"$_ type $tpname = $_"                                                 => tpname == tpe
        case q"$_ class $tpname $_(...$_) extends { ..$_ } with ..$_ { $_ => ..$_ }" =>
          tpname == tpe
        case q"$_ trait $tpname extends { ..$_ } with ..$_ { $_ => ..$_ }"           => tpname == tpe
        case _                                                                       => false
      }
    }

  protected[this] def isTermDefined(termName: String): List[Tree] => Boolean =
    parentBody => {
      val tpe = TermName(termName)
      parentBody.exists {
        // We are not checking in pattern assignments
        case q"$_ val $tname: $_ = $_" => tname == tpe
        case q"$_ var $tname: $_ = $_" => tname == tpe
        case _                         => false
      }
    }

  protected[this] def modifyModuleStatements(
    moduleName: String,
    mod:        List[Tree] => List[Tree]
  ): List[Tree] => List[Tree] =
    parentBody => {
      val tpe = TermName(moduleName)

      val (newStats, modified) =
        parentBody.foldLeft((List.empty[Tree], false)) { case ((newStats, modified), stat) =>
          stat match {
            case q"$mods object $tname extends { ..$earlydefns } with ..$parents { $self => ..$body }"
                if tname == tpe =>
              (newStats :+ q"$mods object $tname extends { ..$earlydefns } with ..$parents { $self => ..${mod(body)} }",
               true
              )
            case other => (newStats :+ other, modified)
          }
        }
      if (modified)
        newStats
      else
        newStats :+ q"object ${tpe} { ..${mod(List.empty)} }"
    }

  protected[this] def addModuleStatements(
    moduleName: String,
    statements: List[Tree]
  ): List[Tree] => List[Tree] =
    modifyModuleStatements(moduleName, _ ++ statements)

  protected[this] def addValDef(
    valName: String,
    valType: Tree,
    value:   Tree
  ): List[Tree] => List[Tree] =
    parentBody =>
      if (isTermDefined(valName)(parentBody))
        parentBody
      else
        parentBody :+ q"val ${TermName(valName)}: $valType = $value"

  /**
   * Compute a case class definition.
   */
  protected[this] def addCaseClassDef(
    name:      String,
    pars:      List[ValDef],
    extending: Option[String] = None
  ): List[Tree] => (List[Tree], Boolean) =
    parentBody =>
      if (isTypeDefined(name)(parentBody))
        (parentBody, false)
      else
        (parentBody :+
           extending.fold(q"case class ${TypeName(name)}(..$pars)")(extending =>
             q"case class ${TypeName(name)}(..$pars) extends ${TypeName(extending)}"
           ),
         true
        )

  protected[this] def addEnum(
    name:    String,
    values:  List[String], // (StringRepresentation, ClassName)
    eq:      Boolean,
    show:    Boolean,
    reuse:   Boolean,
    encoder: Boolean = false,
    decoder: Boolean = false
  ): List[Tree] => List[Tree] =
    parentBody =>
      if (isTypeDefined(name)(parentBody))
        parentBody
      else {
        val enumValues = values.map(EnumValue.fromString)
        addModuleDefs(
          name,
          eq,
          show,
          reuse,
          encoder,
          decoder,
          TypeType.Enum(enumValues),
          _ ++ enumValues.map { enumValue =>
            q"case object ${TermName(enumValue.className)} extends ${TypeName(name)}"
          }
        )(
          parentBody :+ q"sealed trait ${TypeName(name)}"
        )
      }

  protected[this] def addSumTrait(
    name:      String,
    pars:      List[ValDef],
    extending: Option[String] = None
  ): List[Tree] => (List[Tree], Boolean) = { parentBody =>
    if (isTypeDefined(name)(parentBody))
      (parentBody, false)
    else
      (parentBody :+
         extending.fold(q"sealed trait ${TypeName(name)}{..$pars}")(extending =>
           q"sealed trait ${TypeName(name)} extends ${TypeName(extending)} {..$pars}"
         ),
       true
      )
  }

  protected[this] case class EnumValue(asString: String, className: String)
  protected[this] object EnumValue {
    def fromString(asString: String): EnumValue = EnumValue(asString, snakeToCamel(asString))
  }

  protected[this] sealed trait TypeType
  protected[this] object TypeType {
    case object CaseClass extends TypeType
    case class Enum(values: List[EnumValue]) extends TypeType
    case class Sum(discriminator: String) extends TypeType
  }

  /**
   * Compute a companion object with typeclasses.
   */
  protected[this] def addModuleDefs(
    name:          String,
    eq:            Boolean,
    show:          Boolean,
    reuse:         Boolean,
    encoder:       Boolean = false,
    decoder:       Boolean = false,
    typeType:      TypeType = TypeType.CaseClass,
    modStatements: List[Tree] => List[Tree] = identity,
    nestPath:      Option[Tree] = None
  ): List[Tree] => List[Tree] = {
    val n =
      nestPath.fold[Tree](Ident(TypeName(name)))(t => Select(t, TypeName(name)))

    val eqDef = Option.when(eq)(
      q"implicit val ${TermName(s"eq$name")}: cats.Eq[$n] = cats.Eq.fromUniversalEquals"
    )

    val showDef = Option.when(show)(
      q"implicit val ${TermName(s"show$name")}: cats.Show[$n] = cats.Show.fromToString"
    )

    val reuseDef = Option.when(reuse)(
      q"""implicit val ${TermName(s"reuse$name")}: japgolly.scalajs.react.Reusability[$n] = {
              import japgolly.scalajs.react.Reusability
              japgolly.scalajs.react.Reusability.derive
            }
          """
    )

    val codecConfDef = Option
      .when(encoder || decoder)(typeType match {
        case TypeType.Sum(discriminator) =>
          q"implicit protected val jsonConfiguration: io.circe.generic.extras.Configuration = io.circe.generic.extras.Configuration.default.withDiscriminator($discriminator)".some
        case _                           => none
      })
      .flatten

    val encoderDef = Option.when(encoder)(typeType match {
      case TypeType.CaseClass        =>
        q"implicit val ${TermName(s"jsonEncoder$name")}: io.circe.Encoder[$n] = io.circe.generic.semiauto.deriveEncoder[$n].mapJson(_.deepDropNullValues)"
      case TypeType.Enum(enumValues) =>
        val cases =
          enumValues.map(enumValue =>
            cq"${Ident(TermName(enumValue.className))} => ${enumValue.asString}"
          )
        q"implicit val ${TermName(s"jsonEncoder$name")}: io.circe.Encoder[$n] = io.circe.Encoder.encodeString.contramap[$n]{case ..$cases}"
      case TypeType.Sum(_)           =>
        q"implicit val ${TermName(s"jsonDecoder$name")}: io.circe.Encoder[$n] = io.circe.generic.extras.semiauto.deriveConfiguredEncoder[$n]"
    })

    val decoderDef = Option.when(decoder)(typeType match {
      case TypeType.CaseClass        =>
        q"implicit val ${TermName(s"jsonDecoder$name")}: io.circe.Decoder[$n] = io.circe.generic.semiauto.deriveDecoder[$n]"
      case TypeType.Enum(enumValues) =>
        val cases =
          enumValues.map(enumValue =>
            cq"${enumValue.asString} => ${Ident(TermName(enumValue.className))}"
          )
        q"implicit val ${TermName(s"jsonDecoder$name")}: io.circe.Decoder[$n] = io.circe.Decoder.decodeString.emapTry(s => scala.util.Try(s match {case ..$cases}))"
      case TypeType.Sum(_)           =>
        q"implicit val ${TermName(s"jsonDecoder$name")}: io.circe.Decoder[$n] = io.circe.generic.extras.semiauto.deriveConfiguredDecoder[$n]"
    })

    modifyModuleStatements(
      name,
      stats =>
        modStatements(stats) ++ List(eqDef,
                                     showDef,
                                     reuseDef,
                                     codecConfDef,
                                     encoderDef,
                                     decoderDef
        ).flatten
    )
  }
}
