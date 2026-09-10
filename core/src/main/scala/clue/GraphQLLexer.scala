// Copyright (c) 2016-2026 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

/**
 * GraphQL lexical analysis (spec §2.1), for the `gql` macro. Free of macro APIs so it can be
 * unit-tested.
 */
private[clue] object GraphQLLexer {
  sealed trait Token { def pos: Int } // pos: offset into the tokenized text
  object Token       {
    final case class Punct(value: String, pos: Int)    extends Token
    final case class Name(value: String, pos: Int)     extends Token
    final case class IntVal(value: String, pos: Int)   extends Token
    final case class FloatVal(value: String, pos: Int) extends Token
    final case class Str(value: String, pos: Int)      extends Token
    final case class Splice(index: Int, pos: Int)      extends Token
  }
  import Token.*

  final case class LexError(message: String, pos: Int)

  private val punctChars = "!$&():=@[]{}|"

  private def isNameStart(c: Char): Boolean =
    c == '_' || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
  private def isNameCont(c: Char): Boolean  = isNameStart(c) || isDigit(c)
  private def isDigit(c:    Char): Boolean  = c >= '0' && c <= '9'

  /** Tokenize one text. Comments, whitespace, commas, BOM are skipped. */
  def tokenize(text: String): Either[LexError, Vector[Token]] = {
    val n               = text.length
    val tokens          = Vector.newBuilder[Token]
    var i               = 0
    var error: LexError = null

    def readBlockString(start: Int): Unit = {
      val sb  = new StringBuilder
      var j   = start + 3
      var end = -1
      while (j < n && end < 0)
        if (text.charAt(j) == '\\' && j + 3 < n && text.substring(j + 1, j + 4) == "\"\"\"") {
          sb.append("\"\"\""); j += 4
        } else if (
          j + 2 < n && text.charAt(j) == '"' && text
            .charAt(j + 1) == '"' && text.charAt(j + 2) == '"'
        ) {
          end = j + 3
        } else { sb.append(text.charAt(j)); j += 1 }
      if (end < 0) error = LexError("unterminated string", start)
      else { tokens += Str(sb.toString, start); i = end }
    }

    def readString(start: Int): Unit = {
      val sb   = new StringBuilder
      var j    = start + 1
      var done = false
      while (j < n && !done && error == null)
        text.charAt(j) match {
          case '"'               => done = true; j += 1
          case '\n' | '\r'       => error = LexError("unterminated string", start)
          case '\\' if j + 1 < n =>
            text.charAt(j + 1) match {
              case '"'              => sb.append('"'); j += 2
              case '\\'             => sb.append('\\'); j += 2
              case '/'              => sb.append('/'); j += 2
              case 'b'              => sb.append('\b'); j += 2
              case 'f'              => sb.append('\f'); j += 2
              case 'n'              => sb.append('\n'); j += 2
              case 'r'              => sb.append('\r'); j += 2
              case 't'              => sb.append('\t'); j += 2
              case 'u' if j + 5 < n =>
                try { sb.append(Integer.parseInt(text.substring(j + 2, j + 6), 16).toChar); j += 6 }
                catch {
                  case _: NumberFormatException => error = LexError("invalid unicode escape", j)
                }
              case other            => error = LexError(s"invalid escape '\\$other'", j)
            }
          case c                 => sb.append(c); j += 1
        }
      if (error == null && !done) error = LexError("unterminated string", start)
      else if (error == null) { tokens += Str(sb.toString, start); i = j }
    }

    def readNumber(): Unit = {
      val start   = i
      var j       = i
      if (text.charAt(j) == '-') j += 1
      if (j < n && text.charAt(j) == '0') j += 1
      else while (j < n && isDigit(text.charAt(j))) j += 1
      var isFloat = false
      if (j < n && text.charAt(j) == '.' && j + 1 < n && isDigit(text.charAt(j + 1))) {
        isFloat = true; j += 1
        while (j < n && isDigit(text.charAt(j))) j += 1
      }
      if (j < n && (text.charAt(j) == 'e' || text.charAt(j) == 'E')) {
        var k = j + 1
        if (k < n && (text.charAt(k) == '+' || text.charAt(k) == '-')) k += 1
        if (k < n && isDigit(text.charAt(k))) {
          isFloat = true
          while (k < n && isDigit(text.charAt(k))) k += 1
          j = k
        }
      }
      if (j < n && isNameStart(text.charAt(j))) error = LexError("invalid number", start)
      else {
        tokens += (if (isFloat) FloatVal(text.substring(start, j), start)
                   else IntVal(text.substring(start, j), start))
        i = j
      }
    }

    while (i < n && error == null) {
      val c = text.charAt(i)
      if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == ',' || c == '\uFEFF') i += 1
      else if (c == '#') while (i < n && text.charAt(i) != '\n' && text.charAt(i) != '\r') i += 1
      else if (c == '.') {
        if (i + 2 < n && text.charAt(i + 1) == '.' && text.charAt(i + 2) == '.') {
          tokens += Punct("...", i); i += 3
        } else error = LexError("unexpected character '.'", i)
      } else if (punctChars.indexOf(c) >= 0) { tokens += Punct(c.toString, i); i += 1 }
      else if (c == '"') {
        if (i + 2 < n && text.charAt(i + 1) == '"' && text.charAt(i + 2) == '"') readBlockString(i)
        else readString(i)
      } else if (isDigit(c) || (c == '-' && i + 1 < n && isDigit(text.charAt(i + 1)))) readNumber()
      else if (isNameStart(c)) {
        val start = i
        var j     = i + 1
        while (j < n && isNameCont(text.charAt(j))) j += 1
        tokens += Name(text.substring(start, j), start)
        i = j
      } else error = LexError(s"unexpected character '$c'", i)
    }

    if (error != null) Left(error) else Right(tokens.result())
  }

  /**
   * Tokenize the literal parts of a `gql"..."` (as `StringContext.parts`, i.e. `$$`-escaped:
   * unescape `$$` -> `$` first) as ONE document, with a `Splice(i)` token standing in for the i-th
   * hole. Each part must lex on its own (a string literal or comment cannot span a splice: report a
   * LexError "unterminated string" at the offending part). Token positions are offsets into the
   * concatenation of the unescaped parts (holes take no space).
   */
  def tokenizeParts(parts: List[String]): Either[LexError, Vector[Token]] = {
    val unescaped = parts.map(_.replace("$$", "$"))

    def shift(t: Token, by: Int): Token = t match {
      case Punct(v, p)    => Punct(v, p + by)
      case Name(v, p)     => Name(v, p + by)
      case IntVal(v, p)   => IntVal(v, p + by)
      case FloatVal(v, p) => FloatVal(v, p + by)
      case Str(v, p)      => Str(v, p + by)
      case Splice(idx, p) => Splice(idx, p + by)
    }

    def go(idx: Int, offset: Int, acc: Vector[Token]): Either[LexError, Vector[Token]] =
      if (idx >= unescaped.length) Right(acc)
      else
        tokenize(unescaped(idx)) match {
          case Left(e)   => Left(LexError(e.message, e.pos + offset))
          case Right(ts) =>
            val nextOffset = offset + unescaped(idx).length
            val shifted    = ts.map(shift(_, offset))
            val withSplice =
              if (idx < unescaped.length - 1) shifted :+ Splice(idx, nextOffset) else shifted
            go(idx + 1, nextOffset, acc ++ withSplice)
        }

    go(0, 0, Vector.empty)
  }
}
