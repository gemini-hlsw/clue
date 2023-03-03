// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

import cats.data.NonEmptyList

package object model {
  type GraphQLErrors = NonEmptyList1[GraphQLError]
}
