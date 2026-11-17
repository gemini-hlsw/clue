package test

import clue.GraphQLOperation
import clue.annotation.GraphQL
import test.StarWars

@GraphQL
trait HeroQuery extends GraphQLOperation[StarWars] {
  override val document = gql"""
    query {
      hero(episode: EMPIRE) {
        name
      }
    }
  """
}
