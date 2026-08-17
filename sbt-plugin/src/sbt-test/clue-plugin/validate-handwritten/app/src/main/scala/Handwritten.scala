package test

import clue.GraphQLSubquery
import clue.gql
import clue.annotation.GraphQLType

trait StarWars

// A hand-written subquery living in the project's own sources (not produced by the generator).
// `clueCheck` should validate it against the schema. This version is valid.
@GraphQLType("Character")
abstract class HandwrittenSubquery extends GraphQLSubquery[StarWars] {
  override val subquery = gql"{ id name }"
}
