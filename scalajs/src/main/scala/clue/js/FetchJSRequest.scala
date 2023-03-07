// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.js

import org.scalajs.dom.Headers

final case class FetchJSRequest(uri: String, headers: Headers)
