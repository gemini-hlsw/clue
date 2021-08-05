// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model

import cats.Eq
import cats.syntax.either._
import cats.syntax.eq._
import clue.model.GraphQLError.{ Location, PathElement }

final case class GraphQLError(
  message:   String,
  path:      List[PathElement],
  locations: List[Location]
)

object GraphQLError {

  sealed trait PathElement extends Product with Serializable {

    def toEither: Either[Int, String] =
      this match {
        case StringPathElement(e) => e.asRight[Int]
        case IntPathElement(e)    => e.asLeft[String]
      }

    def fold[A](fi: Int => A, fs: String => A): A =
      this match {
        case StringPathElement(e) => fs(e)
        case IntPathElement(e)    => fi(e)
      }

  }

  object PathElement {

    def int(element: Int): PathElement =
      IntPathElement(element)

    def string(element: String): PathElement =
      StringPathElement(element)

    implicit val EqPathElement: Eq[PathElement] =
      Eq.instance {
        case (IntPathElement(a), IntPathElement(b))       => a === b
        case (StringPathElement(a), StringPathElement(b)) => a === b
        case _                                            => false
      }

  }

  final case class StringPathElement(element: String) extends PathElement
  final case class IntPathElement(element: Int) extends PathElement


  final case class Location(line: Int, column: Int)

  object Location {
    implicit val EqLocation: Eq[Location] =
      Eq.by { a =>
        (
          a.line,
          a.column
        )
      }
  }

  implicit val EqGraphQLError: Eq[GraphQLError] =
    Eq.by { a =>
      (
        a.message,
        a.path,
        a.locations
      )
    }

}
