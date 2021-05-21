// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import sttp.model.{ Uri => SUri }
import org.http4s.Uri
import org.http4s.Query

package object http4sjdk {
  protected[http4sjdk] def sttpUriToHttp4sUri(uri: SUri): Uri = Uri(
    scheme = uri.scheme.map(Uri.Scheme.unsafeFromString),
    authority = uri.authority.map(a =>
      Uri.Authority(
        userInfo = a.userInfo.map(ui => Uri.UserInfo(ui.username, ui.password)),
        host = Uri.Ipv4Address
          .fromString(a.host)
          .getOrElse(Uri.Ipv6Address.unsafeFromString(a.host)),
        port = a.port
      )
    ),
    path = Uri.Path(uri.pathSegments.segments.map(s => Uri.Path.Segment(s.v)).toVector),
    query = Query.unsafeFromString(uri.toString),
    fragment = uri.fragment
  )
}
