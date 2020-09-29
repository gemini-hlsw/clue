package clue.macros

import cats.syntax.all._
import scala.reflect.macros.blackbox
import scala.reflect.ClassTag

protected[macros] trait Macro {
  val c: blackbox.Context

  import c.universe._

  /**
   * Abort the macro showing an error message.
   */
  protected[this] def abort(msg: Any): Nothing =
    c.abort(c.enclosingPosition, msg.toString)

  /**
   * Log debug info.
   */
  protected[this] def log(msg: Any): Unit =
    c.info(c.enclosingPosition, msg.toString, force = true)

  /**
   * Log an actual Tree AST (not the Scala code equivalent).
   */
  protected[this] def debugTree(tree: Tree): Unit =
    log(c.universe.showRaw(tree))

  /**
   * Parse a type name (c.parse only parses terms).
   */
  protected[this] def parseType(tpe: String): Tree =
    c.parse(tpe) match {
      case Ident(TermName(name))        => Ident(TypeName(name))
      case Select(tree, TermName(name)) => Select(tree, TypeName(name))
      case other                        =>
        debugTree(other)
        abort(s"Unexpected type [$tpe]")
    }

  /**
   * Extract the unqualified type name from a type.
   */
  protected[this] def unqualifiedType(tpe: Tree): Option[String] = tpe match {
    case Ident(TypeName(tpe))     => tpe.some
    case Select(_, TypeName(tpe)) => tpe.some
    case _                        => none
  }

  /**
   * Get the annotation name.
   */
  protected[this] def macroName: Tree =
    c.prefix.tree match {
      case Apply(Select(New(name), _), _) => name
      case _                              => abort("Unexpected macro application")
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

  // protected[this] def modifyTraitStatements(
  //   traitName: String,
  //   extending: List[Tree],
  //   mod:       List[Tree] => List[Tree]
  // ): List[Tree] => List[Tree] =
  //   parentBody => {
  //     val tpe = TypeName(traitName)

  //     val (newStats, modified) =
  //       parentBody.foldLeft((List.empty[Tree], false)) { case ((newStats, modified), stat) =>
  //         stat match {
  //           case q"$mods trait $tpname extends { ..$earlydefns } with ..$parents { $self => ..$body }"
  //               if tpname == tpe =>
  //             (newStats :+ q"$mods trait $tpname extends { ..$earlydefns } with ..${(parents ++ extending).distinct} { $self => ..${mod(body)} }",
  //              true
  //             )
  //           case other => (newStats :+ other, modified)
  //         }
  //       }
  //     if (modified)
  //       newStats
  //     else
  //       newStats :+ q"trait ${tpe} extends {..${List.empty[Tree]}} with ..$extending { ..${mod(List.empty)} }"
  //   }

  // protected[this] def addTraitStatements(
  //   traitName:  String,
  //   extending:  List[Tree],
  //   statements: List[Tree]
  // ): List[Tree] => List[Tree] =
  //   modifyTraitStatements(traitName, extending, _ ++ statements)

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
    name: String,
    pars: List[ValDef]
  ): List[Tree] => (List[Tree], Boolean) =
    parentBody =>
      if (isTypeDefined(name)(parentBody))
        (parentBody, false)
      else
        (parentBody :+ q"case class ${TypeName(name)}(..$pars)", true)

  protected[this] def addEnum(
    name:    String,
    values:  List[String],
    eq:      Boolean,
    show:    Boolean,
    reuse:   Boolean,
    encoder: Boolean = false,
    decoder: Boolean = false
  ): List[Tree] => List[Tree] =
    parentBody =>
      if (isTypeDefined(name)(parentBody))
        parentBody
      else
        addModuleDefs(name,
                      eq,
                      show,
                      reuse,
                      encoder,
                      decoder,
                      _ ++ values.map { value =>
                        q"case object ${TermName(value)} extends ${TypeName(name)}"
                      }
        )(
          parentBody :+ q"sealed trait ${TypeName(name)}"
        )

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
    modStatements: List[Tree] => List[Tree] = identity,
    nestTree:      Option[Tree] = None
  ): List[Tree] => List[Tree] = {
    val n =
      nestTree.fold[Tree](Ident(TypeName(name)))(t => Select(t, TypeName(name)))

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

    val encoderDef = Option.when(encoder)(
      q"implicit val ${TermName(s"jsonEncoder$name")}: io.circe.Encoder[$n] = io.circe.generic.semiauto.deriveEncoder[$n].mapJson(_.deepDropNullValues)"
    )

    val decoderDef = Option.when(decoder)(
      q"implicit val ${TermName(s"jsonDecoder$name")}: io.circe.Decoder[$n] = io.circe.generic.semiauto.deriveDecoder[$n]"
    )

    modifyModuleStatements(
      name,
      stats =>
        modStatements(stats) ++ List(eqDef, showDef, reuseDef, encoderDef, decoderDef).flatten
    )
  }
}
