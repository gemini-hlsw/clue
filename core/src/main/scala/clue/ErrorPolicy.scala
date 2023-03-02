// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.data.Ior
import cats.data.NonEmptyList
import clue.model.GraphQLError
import cats.effect.Sync
import cats.MonadThrow

sealed trait ErrorPolicy {
  type ReturnType[D]

  // def processData[F[_]: Sync, D](data:     D): F[ReturnType[D]]
  // def processErrors[F[_]: Sync, D](errors: NonEmptyList[GraphQLError]): F[ReturnType[D]]
  // def processBoth[F[_]: Sync, D](data:     D, errors: NonEmptyList[GraphQLError]): F[ReturnType[D]]
  def process[F[_]: Sync, D](result: Ior[NonEmptyList[GraphQLError], D]): F[ReturnType[D]]
}

object ErrorPolicy {
  protected sealed trait Distinct extends ErrorPolicy {
    type ReturnType[D] = D

    protected def processData[F[_]: Sync, D](data: D): F[ReturnType[D]] = Sync[F].delay(data)
    protected def processErrors[F[_]: MonadThrow, D](
      errors: NonEmptyList[GraphQLError]
    ): F[ReturnType[D]] =
      MonadThrow[F].raiseError(ResponseException(errors))
  }

  final case object IgoreOnData extends Distinct {
    def process[F[_]: Sync, D](result: Ior[NonEmptyList[GraphQLError], D]): F[ReturnType[D]] =
      result match {
        case Ior.Left(errors)  => processErrors(errors)
        case Ior.Right(data)   => processData(data)
        case Ior.Both(_, data) => processData(data)
      }
  }

  final case object Raise extends Distinct {
    def process[F[_]: Sync, D](result: Ior[NonEmptyList[GraphQLError], D]): F[ReturnType[D]] =
      result match {
        case Ior.Left(errors)    => processErrors(errors)
        case Ior.Right(data)     => processData(data)
        case Ior.Both(errors, _) => processErrors(errors)
      }

  }

  final case object Return extends ErrorPolicy {
    type ReturnType[D] = Ior[NonEmptyList[GraphQLError], D]

    def process[F[_]: Sync, D](result: Ior[NonEmptyList[GraphQLError], D]): F[ReturnType[D]] =
      Sync[F].delay(result)
  }
}
