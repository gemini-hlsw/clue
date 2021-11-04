package clue

import io.circe.Decoder

trait GraphQLFragment[S, A] {
  def subDocument: String

  implicit def jsonDecoder: Decoder[A]
}

// @GraphQL
// object TargetSubQuery extends SubQuery[Target] {
//   val subDocument = """
//   | fragment targetBaseFields on Target {
//   |   id
//   |   name
//   |   magnitudes {
//   |     band
//   |     etc
//   |   }
//   | }
//   """.stripMargin
// }

// val obsQueryDocument = gql"""
// | $TargetSubQuery
// |
// | query {
// |   observations {
// |     targets {
// |       ...targetBaseFields
// |     }
// |   }
// | }
// """.stripMargin
