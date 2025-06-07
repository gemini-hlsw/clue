// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.js

import cats.Applicative
import cats.MonadThrow
import clue.FetchClientImpl
import org.scalajs.dom.Headers
import org.typelevel.log4cats.Logger

object FetchJsClient {
  def of[F[_], S](uri: String, name: String = "", headers: Headers = new Headers())(using
    F:       MonadThrow[F],
    backend: FetchJsBackend[F],
    logger:  Logger[F]
  ): F[FetchJsClient[F, S]] = {
    val logPrefix = s"clue.FetchJsClient[${if (name.isEmpty) uri else name}]"

    val internalLogger = logger.withModifiedString(s => s"$logPrefix $s")

    Applicative[F].pure(
      new FetchClientImpl[F, FetchJsRequest, S](
        FetchJsRequest(uri, headers)
      )(using F, internalLogger, backend)
    )
  }
}
