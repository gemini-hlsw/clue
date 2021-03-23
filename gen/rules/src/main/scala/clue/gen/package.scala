package clue

import cats.effect.IO

package object gen {
  def abort(msg: String): IO[Nothing] =
    IO.raiseError(new Exception(msg))

  def log(msg: String): IO[Unit] = IO(println(msg))
}
