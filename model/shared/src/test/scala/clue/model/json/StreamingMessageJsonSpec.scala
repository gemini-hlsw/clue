// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model.json

import cats.tests.CatsSuite
import clue.model.StreamingMessage.{ FromClient, FromServer }
import clue.model.arb._
import io.circe.testing.instances._
import io.circe.testing.CodecTests

final class StreamingMessageJsonSpec extends CatsSuite {

  import ArbFromClient._
  import ArbFromServer._

  checkAll("FromClient", CodecTests[FromClient].codec)
  checkAll("FromServer", CodecTests[FromServer].codec)

}
