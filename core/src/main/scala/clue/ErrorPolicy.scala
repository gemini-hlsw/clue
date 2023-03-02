// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.MonadThrow
import cats.data.Ior
import cats.data.NonEmptyList
import cats.effect.Sync
import clue.model.GraphQLError

sealed trait ErrorPolicy {
  type ReturnType[D]

  def processor[D]: ErrorPolicyProcessor[D, ReturnType[D]]
}

sealed trait ErrorPolicyProcessor[D, R] {
  def process[F[_]: Sync](result: Ior[NonEmptyList[GraphQLError], D]): F[R]
}

object ErrorPolicy {
  protected sealed trait Distinct[D] extends ErrorPolicyProcessor[D, D] {
    protected def processData[F[_]: Sync, D](data: D): F[D] = Sync[F].delay(data)
    protected def processErrors[F[_]: MonadThrow, D](errors: NonEmptyList[GraphQLError]): F[D] =
      MonadThrow[F].raiseError(ResponseException(errors))
  }

  object IgoreOnData extends ErrorPolicy {
    // implicit object IgoreOnDataInfo extends ErrorPolicyInfo[ErrorPolicy.IgnoreOnData] {
    type ReturnType[D] = D

    def processor[D]: ErrorPolicyProcessor[D, D] = new Distinct[D] {
      def process[F[_]: Sync](result: Ior[NonEmptyList[GraphQLError], D]): F[D] =
        result match {
          case Ior.Left(errors)  => processErrors(errors)
          case Ior.Right(data)   => processData(data)
          case Ior.Both(_, data) => processData(data)
        }
    }
  }

  object RaiseAlways extends ErrorPolicy {
    // implicit object RaiseAlwaysInfo extends ErrorPolicyInfo[ErrorPolicy.RaiseAlways] {
    type ReturnType[D] = D

    def processor[D]: ErrorPolicyProcessor[D, D] = new Distinct[D] {
      def process[F[_]: Sync](result: Ior[NonEmptyList[GraphQLError], D]): F[ReturnType[D]] =
        result match {
          case Ior.Left(errors)    => processErrors(errors)
          case Ior.Right(data)     => processData(data)
          case Ior.Both(errors, _) => processErrors(errors)
        }
    }
  }

  object ReturnAlways extends ErrorPolicy {
    // implicit object ReturnAlwaysInfo extends ErrorPolicyInfo[ErrorPolicy.ReturnAlways] {
    type ReturnType[D] = Ior[NonEmptyList[GraphQLError], D]

    def processor[D]: ErrorPolicyProcessor[D, Ior[NonEmptyList[GraphQLError], D]] =
      new ErrorPolicyProcessor[D, Ior[NonEmptyList[GraphQLError], D]] {

        def process[F[_]: Sync](result: Ior[NonEmptyList[GraphQLError], D]): F[ReturnType[D]] =
          Sync[F].delay(result)
      }
  }
}
