// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.http4s

import org.http4s.Header
import org.http4s.Request
import org.typelevel.ci.CIString
import clue.FetchClientWithPars
import clue.TraceHeaderInjector

type Http4sHttpClient[F[_], S] = FetchClientWithPars[F, Request[F], S]

given [F[_]]: TraceHeaderInjector[Request[F]] with
  def addHeaders(params: Request[F], headers: Map[String, String]): Request[F] =
    headers.foldLeft(params):
      case (req, (k, v)) => req.putHeaders(Header.Raw(CIString(k), v))
