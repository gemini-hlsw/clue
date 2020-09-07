// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package clue

class GraphQLException(msg: String) extends Exception(msg)

class InvalidSubscriptionIdException(id: String) extends Exception(s"Invalid subscription id: $id")
