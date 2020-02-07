package clue

import io.circe.Json

class GraphQLException(val errors: List[Json]) extends Exception(errors.toString)

class InvalidSubscriptionIdException(id: String) extends Exception(s"Invalid subscription id: $id")
