package test

import clue.GraphQLSubquery
import clue.annotation.GraphQLType

trait StarWars

// Same hand-written subquery, but now referencing `invalidField`, which doesn't exist on
// `Character`. `clueCheck` must fail.
@GraphQLType("Character")
abstract class HandwrittenSubquery extends GraphQLSubquery[StarWars] {
  override val subquery = gql"{ id invalidField }"
}
