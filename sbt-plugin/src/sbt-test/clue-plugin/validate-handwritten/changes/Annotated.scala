package test

import clue.GraphQLOperation
import clue.annotation.GraphQL

trait StarWars

// `@GraphQL` in the project's own sources (not the clue directory) is misplaced: it's only
// processed by the generator. `clueCheck` must fail with an informative error.
@GraphQL
trait AnnotatedInMain extends GraphQLOperation[StarWars] {
  override val document: String = "query { hero(episode: NEWHOPE) { id } }"
}
