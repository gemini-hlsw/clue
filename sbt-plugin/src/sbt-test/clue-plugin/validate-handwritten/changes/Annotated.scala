package test

import clue.GraphQLOperation
import clue.annotation.GraphQL

trait StarWars

// `@GraphQL` in the project's own sources (not the clue directory) is misplaced: it's only
// processed by the generator. Validation reports this as a warning (not an error), so `compile`
// and `clueCheck` still succeed.
@GraphQL
trait AnnotatedInMain extends GraphQLOperation[StarWars] {
  override val document = gql"query { hero(episode: NEWHOPE) { id } }"
}
