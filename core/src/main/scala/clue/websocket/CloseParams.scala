// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.websocket

import cats.syntax.all.*

case class CloseParams(code: Option[Int] = none, reason: Option[String] = none):
  def show: String = code.map(c => s"$c: ").orEmpty + reason.getOrElse("No reason provided")

object CloseParams:
  def apply(code:   Int): CloseParams               = CloseParams(code = code.some)
  def apply(reason: String): CloseParams            = CloseParams(reason = reason.some)
  def apply(code: Int, reason: String): CloseParams =
    CloseParams(code = code.some, reason = reason.some)
