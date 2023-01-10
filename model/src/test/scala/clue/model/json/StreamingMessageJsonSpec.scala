// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue.model.json

import clue.model.StreamingMessage.FromClient
import clue.model.StreamingMessage.FromServer
import clue.model.arb._
import io.circe.testing.CodecTests
import io.circe.testing.instances._
import munit.DisciplineSuite

final class StreamingMessageJsonSpec extends DisciplineSuite {

  import ArbFromClient._
  import ArbFromServer._

  checkAll("FromClient", CodecTests[FromClient].codec)
  checkAll("FromServer", CodecTests[FromServer].codec)
}
