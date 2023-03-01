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

  def processData[F[_]: Sync, D](data:     D): F[ReturnType[D]]
  def processErrors[F[_]: Sync, D](errors: NonEmptyList[GraphQLError]): F[ReturnType[D]]
  def processBoth[F[_]: Sync, D](data:     D, errors: NonEmptyList[GraphQLError]): F[ReturnType[D]]
}

object ErrorPolicy {
  protected sealed trait Distinct extends ErrorPolicy {
    type ReturnType[D] = D

    def processData[F[_]: Sync, D](data: D): F[ReturnType[D]] = Sync[F].delay(data)
    def processErrors[F[_]: Sync, D](errors: NonEmptyList[GraphQLError]): F[ReturnType[D]] =
      MonadThrow[F].raiseError(ResponseException(errors))
  }

  final case object IgoreOnData extends Distinct {
    def processBoth[F[_]: Sync, D](data: D, errors: NonEmptyList[GraphQLError]): F[ReturnType[D]] =
      processData(data)
  }

  final case object Raise extends Distinct {
    def processBoth[F[_]: Sync, D](data: D, errors: NonEmptyList[GraphQLError]): F[ReturnType[D]] =
      processErrors(errors)

  }

  final case object Return extends ErrorPolicy {
    type ReturnType[D] = Ior[NonEmptyList[GraphQLError], D]

    def processData[F[_]: Sync, D](data: D): F[ReturnType[D]] = Sync[F].delay(Ior.right(data))
    def processErrors[F[_]: Sync, D](errors: NonEmptyList[GraphQLError]): F[ReturnType[D]]        =
      Sync[F].delay(Ior.left(errors))
    def processBoth[F[_]: Sync, D](data: D, errors: NonEmptyList[GraphQLError]): F[ReturnType[D]] =
      Sync[F].delay(Ior.both(errors, data))
  }
}
