package clue.model.arb

import clue.model.GraphQLRequest
import clue.model.StreamingMessage.FromClient
import clue.model.StreamingMessage.FromClient._
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary._

trait ArbFromClient {

  import ArbGraphQLRequest._

  implicit val arbConnectionInit: Arbitrary[ConnectionInit] =
    Arbitrary {
      arbitrary[Map[String, String]].map(ConnectionInit(_))
    }

  implicit val arbStart: Arbitrary[Start] =
    Arbitrary {
      for {
        i <- arbitrary[String]
        p <- arbitrary[GraphQLRequest]
      } yield Start(i, p)
    }

  implicit val arbStop: Arbitrary[Stop] =
    Arbitrary {
      arbitrary[String].map(Stop(_))
    }

  implicit val arbFromClient: Arbitrary[FromClient] =
    Arbitrary {
      Gen.oneOf[FromClient](
        arbitrary[ConnectionInit],
        arbitrary[Start],
        arbitrary[Stop],
        Gen.const(ConnectionTerminate)
      )
    }

}

object ArbFromClient extends ArbFromClient
