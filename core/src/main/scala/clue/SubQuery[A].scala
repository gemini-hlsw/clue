// package clue

import io.circe.Decoder

trait SubQuery[A] {
  def subDocument: String

  implicit def jsonDecoder: Decoder[A]
}

// object TargetSubQuery extends SubQuery[Target] {
//   val subDocument = """
//     id
//     name
//     magnitudes {
//       band
//       etc
//     }
//   """
// }

// val obsQueryDocument = gql"""
//   query {
//     observations {
//       targets {
//         $TargetSubQuery
//       }
//     }
//   }
// """
