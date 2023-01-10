// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.effect.IO

package object gen {
  def abort(msg: String): IO[Nothing] =
    IO.raiseError(new Exception(msg))

  def log(msg: String): IO[Unit] = IO(println(msg))
}
