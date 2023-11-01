// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.Applicative
import cats.ApplicativeThrow
import cats.data.Ior
import cats.syntax.all._
import clue.model.GraphQLDataResponse
import clue.model.GraphQLErrors
import clue.model.GraphQLResponse

sealed trait ErrorPolicy {
  type ReturnType[D]

  def processor[D]: ErrorPolicyProcessor[D, ReturnType[D]]
}

sealed trait ErrorPolicyProcessor[D, R] {
  def process[F[_]: ApplicativeThrow](response: GraphQLResponse[D]): F[R]
}

object ErrorPolicy {
  protected sealed trait Distinct[D] extends ErrorPolicyProcessor[D, D] {
    protected def processData[F[_]: Applicative](data: D): F[D] = Applicative[F].pure(data)
    protected def processErrors[F[_]: ApplicativeThrow](
      errors: GraphQLErrors,
      data:   Option[D] = none
    ): F[D] =
      ApplicativeThrow[F].raiseError(ResponseException(errors, data))
  }

  object IgnoreOnData extends ErrorPolicy {
    type ReturnType[D] = D

    def processor[D]: ErrorPolicyProcessor[D, D] = new Distinct[D] {
      def process[F[_]: ApplicativeThrow](response: GraphQLResponse[D]): F[D] =
        response.result match {
          case Ior.Left(errors)  => processErrors(errors)
          case Ior.Right(data)   => processData(data)
          case Ior.Both(_, data) => processData(data)
        }
    }
  }

  object RaiseAlways extends ErrorPolicy {
    type ReturnType[D] = D

    def processor[D]: ErrorPolicyProcessor[D, D] = new Distinct[D] {
      def process[F[_]: ApplicativeThrow](response: GraphQLResponse[D]): F[ReturnType[D]] =
        response.result match {
          case Ior.Left(errors)       => processErrors(errors)
          case Ior.Right(data)        => processData(data)
          case Ior.Both(errors, data) => processErrors(errors, data.some)
        }
    }
  }

  // This is the only one that preserves extensions.
  object ReturnAlways extends ErrorPolicy {
    type ReturnType[D] = GraphQLResponse[D]

    def processor[D]: ErrorPolicyProcessor[D, GraphQLResponse[D]] =
      new ErrorPolicyProcessor[D, GraphQLResponse[D]] {
        def process[F[_]: ApplicativeThrow](
          response: GraphQLResponse[D]
        ): F[ReturnType[D]] =
          Applicative[F].pure(response)
      }
  }

  object RaiseOnNoData extends ErrorPolicy {
    type ReturnType[D] = GraphQLDataResponse[D]

    def processor[D]: ErrorPolicyProcessor[D, GraphQLDataResponse[D]] =
      new ErrorPolicyProcessor[D, GraphQLDataResponse[D]] {

        def process[F[_]: ApplicativeThrow](
          response: GraphQLResponse[D]
        ): F[ReturnType[D]] =
          response.result match {
            case Ior.Left(errors)       => ApplicativeThrow[F].raiseError(ResponseException(errors, none))
            case Ior.Right(data)        => Applicative[F].pure(GraphQLDataResponse(data, none, none))
            case Ior.Both(errors, data) =>
              Applicative[F].pure(GraphQLDataResponse(data, errors.some, none))
          }

      }
  }
}
