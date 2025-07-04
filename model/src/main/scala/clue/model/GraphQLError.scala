// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model

import cats.Eq
import cats.data.NonEmptyList
import cats.syntax.either.*
import cats.syntax.eq.*
import cats.syntax.option.*
import clue.model.GraphQLError.Location
import clue.model.GraphQLError.PathElement

/**
 * A GraphQL error.
 *
 * See https://spec.graphql.org/October2021/#sec-Errors
 *
 * @param message
 *   string description of the error
 * @param path
 *   list of path segments starting at the root of the response, in case the error can be associated
 *   to a particular field in the GraphQL result
 * @param locations
 *   lines and columns, in case the error can be associated to particular points in the requested
 *   GraphQL document
 * @param extensions
 *   values for protocol extension
 */
final case class GraphQLError(
  message:    String,
  path:       Option[NonEmptyList[PathElement]] = none,
  locations:  Option[NonEmptyList[Location]] = none,
  extensions: Option[GraphQLExtensions] = none
)

object GraphQLError:
  sealed trait PathElement extends Product with Serializable:

    def toEither: Either[Int, String] =
      this match
        case StringPathElement(e) => e.asRight[Int]
        case IntPathElement(e)    => e.asLeft[String]

    def fold[A](fi: Int => A, fs: String => A): A =
      this match
        case StringPathElement(e) => fs(e)
        case IntPathElement(e)    => fi(e)

  object PathElement:
    def int(element: Int): PathElement =
      IntPathElement(element)

    def string(element: String): PathElement =
      StringPathElement(element)

    given Eq[PathElement] =
      Eq.instance:
        case (IntPathElement(a), IntPathElement(b))       => a === b
        case (StringPathElement(a), StringPathElement(b)) => a === b
        case _                                            => false

  final case class StringPathElement(element: String) extends PathElement
  final case class IntPathElement(element: Int)       extends PathElement

  final case class Location(line: Int, column: Int)

  object Location:
    given Eq[Location] =
      Eq.by: a =>
        (a.line, a.column)

  given Eq[GraphQLError] =
    Eq.by: a =>
      (a.message, a.path, a.locations, a.extensions)
