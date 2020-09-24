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

  protected[this] def typeNameToTermName(tree: Tree): Tree =
    tree match {
      case Ident(name)             => Ident(name.toTermName)
      case Select(qualifier, name) => Select(typeNameToTermName(qualifier), name.toTermName)
      case _                       => tree
    }

  /**
   * Compute a case class definition.
   */
  protected[this] def caseClassDef(
    name:   String,
    pars:   List[ValDef],
    lenses: Boolean
  ): Tree = {
    val n = TypeName(name)
    if (lenses)
      q"@monocle.macros.Lenses case class $n(..$pars)"
    else
      q"case class $n(..$pars)"
  }

  /**
   * Compute a companion object with typeclasses.
   */
  protected[this] def moduleDef(
    name:       String,
    eq:         Boolean,
    show:       Boolean,
    reuse:      Boolean,
    encoder:    Boolean = false,
    decoder:    Boolean = false,
    statements: List[Tree] = List.empty
  ): c.Tree = {
    val n = TypeName(name)

    val eqDef =
      if (eq) q"implicit val ${TermName(s"eq$name")}: cats.Eq[$n] = cats.Eq.fromUniversalEquals"
      else EmptyTree

    val showDef =
      if (show) q"implicit val ${TermName(s"show$name")}: cats.Show[$n] = cats.Show.fromToString"
      else EmptyTree

    val reuseDef =
      if (reuse)
        q"""implicit val ${TermName(s"reuse$name")}: japgolly.scalajs.react.Reusability[$n] = {
              import japgolly.scalajs.react.Reusability
              japgolly.scalajs.react.Reusability.derive
            }
          """
      else EmptyTree

    val encoderDef =
      if (encoder)
        q"implicit val ${TermName(s"jsonEncoder$name")}: io.circe.Encoder[$n] = io.circe.generic.semiauto.deriveEncoder[$n]"
      else EmptyTree

    val decoderDef =
      if (decoder)
        q"implicit val ${TermName(s"jsonDecoder$name")}: io.circe.Decoder[$n] = io.circe.generic.semiauto.deriveDecoder[$n]"
      else EmptyTree

    q"""object ${TermName(name)} {
          ..$statements
          $eqDef
          $showDef
          $reuseDef
          $encoderDef
          $decoderDef
        }"""
  }
}
