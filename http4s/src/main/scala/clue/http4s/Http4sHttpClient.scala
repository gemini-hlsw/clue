// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.http4s

import cats.Applicative
import cats.effect.Sync
import clue.FetchClient
import clue.FetchClientImpl
import org.http4s.Headers
import org.http4s.Method._
import org.http4s.Request
import org.http4s.Uri
import org.typelevel.log4cats.Logger
// import org.http4s.circe._
// import org.http4s.client.Client
// import org.http4s.client.dsl.Http4sClientDsl

object Http4sHttpClient {
  def of[F[_], S](uri: Uri, name: String = "", headers: Headers = Headers.empty)(implicit
    F:       Sync[F],
    backend: Http4sHttpBackend[F],
    logger:  Logger[F]
  ): F[FetchClient[F, S]] = {
    val logPrefix = s"clue.FetchClient[${if (name.isEmpty) uri else name}]"

    val internalLogger = logger.withModifiedString(s => s"$logPrefix $s")

    Applicative[F].pure(
      new FetchClientImpl[F, Request[F], S](
        Request(POST, uri, headers = headers)
      )(F, internalLogger, backend)
    )
  }
}
