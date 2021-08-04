package clue.`http4sjdk-demo`

import cats.effect.IOApp
import cats.effect.IO
import clue.http4sjdk.Http4sJDKWSBackend
import clue.ApolloWebSocketClient
import clue.GraphQLOperation
import io.circe.Encoder
import io.circe.Decoder
import io.circe.Json
import org.typelevel.log4cats.slf4j.Slf4jLogger
import sttp.model.Uri

object Demo extends IOApp.Simple {
  def run =
    Slf4jLogger.create[IO].flatMap { implicit logger =>
      Http4sJDKWSBackend[IO].use { implicit backend =>
        ApolloWebSocketClient
          .of[IO, Unit](
            Uri.parse("wss://lucuma-odb-development.herokuapp.com/ws").getOrElse(???)
          )
          .flatMap { implicit client =>
            client.connect() >>
              client.initialize() >>
              client
                .request(new GraphQLOperation[Unit] {
                  type Data      = Json
                  type Variables = Json

                  override val document: String = """
                  |query {
                  |  observations(programId: "p-2") {
                  |    nodes {
                  |      id
                  |      name
                  |    }
                  |  }
                  |}""".stripMargin

                  override val varEncoder: Encoder[Variables] = Encoder[Json]

                  override val dataDecoder: Decoder[Data] = Decoder[Json]

                })
                .flatMap(IO.println) >> client.terminate() >> client.disconnect()
          }
          .onError(t => IO(t.printStackTrace()))
      }
    }
}
