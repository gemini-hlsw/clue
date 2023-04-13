// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

// Used to build convenience methods in generated queries.
abstract class ClientAppliedF[F[_], S, AFP[_[_], _]] {
  def applyP[P](client: FetchClientWithPars[F, P, S]): AFP[F, P]
}

object ClientAppliedF {
  implicit def clientApplyP[F[_], P, S, AFP[_[_], _]](
    applied: ClientAppliedF[F, S, AFP]
  )(implicit
    client:  clue.FetchClientWithPars[F, P, S]
  ): AFP[F, P] =
    applied.applyP(client)
}
