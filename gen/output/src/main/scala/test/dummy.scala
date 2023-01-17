// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package japgolly.scalajs.react

import scala.annotation.unused

object Dummy {
  trait Something

  implicit val anyReuse: Reusability[Any] = Reusability[Any]()
}

case class Reusability[A]()
object Reusability {
  def derive[A](implicit @unused r: Reusability[Any]): Reusability[A] =
    Reusability[A]()
}
