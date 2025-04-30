// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import org.http4s.Request

package object http4s {
  type Http4sHttpClient[F[_], S] = FetchClientWithPars[F, Request[F], S]
}
