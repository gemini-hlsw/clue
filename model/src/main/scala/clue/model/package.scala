// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.data.NonEmptyList
import io.circe.Json

package object model {
  type GraphQLErrors = NonEmptyList[GraphQLError]

  type GraphQLExtensions = Map[String, Json]
}
