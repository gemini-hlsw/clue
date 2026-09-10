// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

/**
 * Token-level utilities over `Vector[GraphQLLexer.Token]`, for the `gql` macro. Macro-free so it
 * can be unit-tested.
 */
private[clue] object GraphQLDocumentScan {
  import GraphQLLexer.Token

  /** A GraphQL type reference as written in a var-def. */
  sealed trait VarType { def nonNull: Boolean; def render: String }
  object VarType       {
    final case class Named(name: String, nonNull: Boolean)    extends VarType {
      def render: String = if (nonNull) s"$name!" else name
    }
    final case class ListOf(inner: VarType, nonNull: Boolean) extends VarType {
      def render: String = s"[${inner.render}]" + (if (nonNull) "!" else "")
    }
  }
  import VarType.*

  private def stripTopNonNull(t: VarType): VarType = t match {
    case Named(n, _)  => Named(n, nonNull = false)
    case ListOf(i, _) => ListOf(i, nonNull = false)
  }

  /**
   * GraphQL "is variable usage allowed", as `main` implements it today: same type modulo top-level
   * nullability, and a non-null requirement needs a non-null declaration.
   */
  def usableAs(declared: VarType, required: VarType): Boolean =
    stripTopNonNull(declared) == stripTopNonNull(
      required
    ) && (!required.nonNull || declared.nonNull)

  final case class ScanError(message: String, pos: Int)

  private def posOf(tokens: Vector[Token], i: Int): Int = tokens.lift(i).map(_.pos).getOrElse(-1)

  private def isPunct(tokens: Vector[Token], i: Int, v: String): Boolean =
    tokens.lift(i) match {
      case Some(Token.Punct(p, _)) => p == v
      case _                       => false
    }

  // Type :: NamedType "!"? | ListType "!"?
  private def parseType(tokens: Vector[Token], i: Int): Either[ScanError, (VarType, Int)] =
    tokens.lift(i) match {
      case Some(Token.Name(n, _))    =>
        val nn = isPunct(tokens, i + 1, "!")
        Right((Named(n, nn), i + (if (nn) 2 else 1)))
      case Some(Token.Punct("[", _)) =>
        parseType(tokens, i + 1).flatMap { case (inner, j) =>
          if (!isPunct(tokens, j, "]")) Left(ScanError("expected ']'", posOf(tokens, j)))
          else {
            val nn = isPunct(tokens, j + 1, "!")
            Right((ListOf(inner, nn), j + (if (nn) 2 else 1)))
          }
        }
      case _                         => Left(ScanError("expected a type", posOf(tokens, i)))
    }

  // Skip one Value: a single scalar/enum token, a `$variable`, or a balanced `[...]`/`{...}` (nested
  // strings are already atomic tokens, so counting only the outermost bracket kind is enough).
  private def skipValue(tokens: Vector[Token], i: Int): Either[ScanError, Int] =
    tokens.lift(i) match {
      case Some(Token.Punct(o @ ("[" | "{" | "("), _)) =>
        val c                                                = o match { case "[" => "]"; case "{" => "}"; case _ => ")" }
        def loop(j: Int, depth: Int): Either[ScanError, Int] =
          tokens.lift(j) match {
            case Some(Token.Punct(`o`, _)) => loop(j + 1, depth + 1)
            case Some(Token.Punct(`c`, _)) =>
              if (depth == 1) Right(j + 1) else loop(j + 1, depth - 1)
            case Some(_)                   => loop(j + 1, depth)
            case None                      => Left(ScanError(s"unterminated '$o'", posOf(tokens, i)))
          }
        loop(i + 1, 1)
      case Some(Token.Punct("$", _))                   =>
        tokens.lift(i + 1) match {
          case Some(Token.Name(_, _)) => Right(i + 2)
          case _                      => Left(ScanError("expected variable name", posOf(tokens, i + 1)))
        }
      case Some(_)                                     => Right(i + 1)
      case None                                        => Left(ScanError("expected a value", posOf(tokens, i)))
    }

  // Skip an optional `= Value` default, then any number of `@ Name (Arguments)?` directives.
  private def skipDefaultAndDirectives(tokens: Vector[Token], i: Int): Either[ScanError, Int] = {
    def directives(j: Int): Either[ScanError, Int] =
      if (!isPunct(tokens, j, "@")) Right(j)
      else
        tokens.lift(j + 1) match {
          case Some(Token.Name(_, _)) =>
            val afterName = j + 2
            (if (isPunct(tokens, afterName, "(")) skipValue(tokens, afterName)
             else Right(afterName))
              .flatMap(directives)
          case _                      => Left(ScanError("expected directive name", posOf(tokens, j + 1)))
        }

    (if (isPunct(tokens, i, "=")) skipValue(tokens, i + 1) else Right(i)).flatMap(directives)
  }

  /**
   * Parse a parenthesized var-def list `($a: T = default @dir, $b: [U!]!)` given as tokens (the
   * whole token stream is the list, parens included). Defaults (any Value, possibly nested
   * lists/objects/strings) and directives are skipped.
   */
  def parseVarDefs(tokens: Vector[Token]): Either[ScanError, Map[String, VarType]] = {
    def loop(i: Int, acc: Map[String, VarType]): Either[ScanError, Map[String, VarType]] =
      if (isPunct(tokens, i, ")")) Right(acc)
      else if (!isPunct(tokens, i, "$")) Left(ScanError("expected '$' or ')'", posOf(tokens, i)))
      else
        tokens.lift(i + 1) match {
          case Some(Token.Name(name, _)) =>
            if (!isPunct(tokens, i + 2, ":")) Left(ScanError("expected ':'", posOf(tokens, i + 2)))
            else
              parseType(tokens, i + 3).flatMap { case (tpe, j) =>
                skipDefaultAndDirectives(tokens, j).flatMap(next => loop(next, acc + (name -> tpe)))
              }
          case _                         => Left(ScanError("expected variable name", posOf(tokens, i + 1)))
        }

    if (!isPunct(tokens, 0, "(")) Left(ScanError("expected '('", posOf(tokens, 0)))
    else loop(1, Map.empty)
  }

  /**
   * The operation header's var-defs of a document: skip to the first
   * `query`/`mutation`/`subscription` Name at brace-depth 0 (fragments may precede it), skip an
   * optional operation Name, and if the next token is `(` parse the var-defs up to the matching
   * `)`. A shorthand `{ ... }` document, or an operation without parens, declares none. Returns the
   * map and the index of the first token AFTER the header (or after the keyword/name when there is
   * no header; 0 when no keyword is found).
   */
  def operationVars(tokens: Vector[Token]): Either[ScanError, (Map[String, VarType], Int)] = {
    @scala.annotation.tailrec
    def findHeader(i: Int, depth: Int): Int =
      if (i >= tokens.length) -1
      else
        tokens(i) match {
          case Token.Punct("{", _) => findHeader(i + 1, depth + 1)
          case Token.Punct("}", _) => findHeader(i + 1, depth - 1)
          case Token.Name(n, _)
              if depth == 0 && (n == "query" || n == "mutation" || n == "subscription") =>
            i
          case _                   => findHeader(i + 1, depth)
        }

    @scala.annotation.tailrec
    def matchParen(j: Int, depth: Int): Int =
      if (j >= tokens.length) -1
      else
        tokens(j) match {
          case Token.Punct("(", _) => matchParen(j + 1, depth + 1)
          case Token.Punct(")", _) => if (depth == 1) j else matchParen(j + 1, depth - 1)
          case _                   => matchParen(j + 1, depth)
        }

    val kwIdx = findHeader(0, 0)
    if (kwIdx < 0) Right((Map.empty, 0))
    else {
      val afterKw   = kwIdx + 1
      val afterName = tokens.lift(afterKw) match {
        case Some(Token.Name(_, _)) => afterKw + 1
        case _                      => afterKw
      }
      if (!isPunct(tokens, afterName, "(")) Right((Map.empty, afterName))
      else {
        val closeIdx = matchParen(afterName + 1, 1)
        if (closeIdx < 0) Left(ScanError("unterminated var-defs", posOf(tokens, afterName)))
        else parseVarDefs(tokens.slice(afterName, closeIdx + 1)).map(vars => (vars, closeIdx + 1))
      }
    }
  }
}
