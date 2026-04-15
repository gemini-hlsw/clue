// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.http4s

import cats.MonadThrow
import cats.effect.Concurrent
import cats.syntax.applicative.*
import clue.FetchClientImpl
import org.http4s.Headers
import org.http4s.Method.*
import org.http4s.Request
import org.http4s.Uri
import org.http4s.client.Client
import org.typelevel.log4cats.Logger

object Http4sHttpClient {
  def of[F[_]: {MonadThrow as F, Http4sHttpBackend as B, Logger as L}, S](
    uri:     Uri,
    name:    String = "",
    headers: Headers = Headers.empty
  ): F[Http4sHttpClient[F, S]] = {
    val logPrefix = s"clue.FetchClientWithPars[${if (name.isEmpty) uri else name}]"

    val internalLogger = L.withModifiedString(s => s"$logPrefix $s")

    new FetchClientImpl[F, Request[F], S](
      Request(POST, uri, headers = headers)
    )(using F, internalLogger, B).pure
  }

  def of[F[_]: {Concurrent as F, Logger as L}, S](
    uri:     Uri,
    client:  Client[F],
    name:    String,
    headers: Headers
  ): F[Http4sHttpClient[F, S]] = {
    val logPrefix = s"clue.FetchClientWithPars[${if (name.isEmpty) uri else name}]"

    val internalLogger = L.withModifiedString(s => s"$logPrefix $s")

    val clientBackend = Http4sHttpBackend[F](client)

    new FetchClientImpl[F, Request[F], S](
      Request(POST, uri, headers = headers)
    )(using F, internalLogger, clientBackend).pure
  }

  def of[F[_]: Concurrent: Logger, S](uri: Uri, client: Client[F]): F[Http4sHttpClient[F, S]] =
    of[F, S](uri, client, "", Headers.empty)

  def of[F[_]: Concurrent: Logger, S](
    uri:    Uri,
    client: Client[F],
    name:   String
  ): F[Http4sHttpClient[F, S]] =
    of[F, S](uri, client, name, Headers.empty)

}
