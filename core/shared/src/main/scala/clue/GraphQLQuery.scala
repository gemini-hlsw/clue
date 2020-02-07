package clue

import io.circe.{ Decoder, Encoder }

trait GraphQLQuery {
  val document: String
  type Variables
  type Data

  implicit val varEncoder: Encoder[Variables]
  implicit val dataDecoder: Decoder[Data]
}
