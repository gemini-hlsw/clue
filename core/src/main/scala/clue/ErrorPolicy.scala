// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.Applicative
import cats.ApplicativeThrow
import cats.data.Ior
import cats.syntax.all._
import clue.model.GraphQLCombinedResponse
import clue.model.GraphQLDataResponse
import clue.model.GraphQLErrors

sealed trait ErrorPolicy {
  type ReturnType[D]

  def processor[D]: ErrorPolicyProcessor[D, ReturnType[D]]
}

sealed trait ErrorPolicyProcessor[D, R] {
  def process[F[_]: ApplicativeThrow](result: GraphQLCombinedResponse[D]): F[R]
}

object ErrorPolicy {
  protected sealed trait Distinct[D] extends ErrorPolicyProcessor[D, D] {
    protected def processData[F[_]: Applicative, D](data: D): F[D] = Applicative[F].pure(data)
    protected def processErrors[F[_]: ApplicativeThrow, D](errors: GraphQLErrors): F[D] =
      ApplicativeThrow[F].raiseError(ResponseException(errors))
  }

  object IgnoreOnData extends ErrorPolicy {
    type ReturnType[D] = D

    def processor[D]: ErrorPolicyProcessor[D, D] = new Distinct[D] {
      def process[F[_]: ApplicativeThrow](result: GraphQLCombinedResponse[D]): F[D] =
        result match {
          case Ior.Left(errors)  => processErrors(errors)
          case Ior.Right(data)   => processData(data)
          case Ior.Both(_, data) => processData(data)
        }
    }
  }

  object RaiseAlways extends ErrorPolicy {
    type ReturnType[D] = D

    def processor[D]: ErrorPolicyProcessor[D, D] = new Distinct[D] {
      def process[F[_]: ApplicativeThrow](result: GraphQLCombinedResponse[D]): F[ReturnType[D]] =
        result match {
          case Ior.Left(errors)    => processErrors(errors)
          case Ior.Right(data)     => processData(data)
          case Ior.Both(errors, _) => processErrors(errors)
        }
    }
  }

  object ReturnAlways extends ErrorPolicy {
    type ReturnType[D] = GraphQLCombinedResponse[D]

    def processor[D]: ErrorPolicyProcessor[D, GraphQLCombinedResponse[D]] =
      new ErrorPolicyProcessor[D, GraphQLCombinedResponse[D]] {

        def process[F[_]: ApplicativeThrow](result: GraphQLCombinedResponse[D]): F[ReturnType[D]] =
          Applicative[F].pure(result)
      }
  }

  object RaiseOnNoData extends ErrorPolicy {
    type ReturnType[D] = GraphQLDataResponse[D]

    def processor[D]: ErrorPolicyProcessor[D, GraphQLDataResponse[D]] =
      new ErrorPolicyProcessor[D, GraphQLDataResponse[D]] {

        def process[F[_]: ApplicativeThrow](result: GraphQLCombinedResponse[D]): F[ReturnType[D]] =
          result match {
            case Ior.Left(errors)       => ApplicativeThrow[F].raiseError(ResponseException(errors))
            case Ior.Right(data)        => Applicative[F].pure(GraphQLDataResponse(data, none, none))
            case Ior.Both(errors, data) =>
              Applicative[F].pure(GraphQLDataResponse(data, errors.some, none))
          }

      }
  }
}
