package clue.`http4sjdk-demo`

import cats.Applicative
import cats.effect.Async
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import cats.effect.Sync
import cats.syntax.all._
import clue.ApolloWebSocketClient
import clue.GraphQLOperation
import clue.PersistentStreamingClient
import clue.http4sjdk.Http4sJDKWSBackend
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import org.http4s.implicits._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.duration._

object Demo extends IOApp.Simple {

  object Query extends GraphQLOperation[Unit] {
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
  }

  object Subscription extends GraphQLOperation[Unit] {
    type Data      = Json
    type Variables = Json

    override val document: String = """
    |subscription {
    |  observationEdit(programId:"p-2") {
    |    id
    |  }
    |}""".stripMargin

    override val varEncoder: Encoder[Variables] = Encoder[Json]

    override val dataDecoder: Decoder[Data] = Decoder[Json]
  }

  def withLogger[F[_]: Sync]: Resource[F, Logger[F]] =
    Resource.make(Slf4jLogger.create[F])(_ => Applicative[F].unit)

  def withStreamingClient[F[_]: Async: Logger]
    : Resource[F, PersistentStreamingClient[F, Unit, _, _]] =
    for {
      backend <- Http4sJDKWSBackend[F]
      uri      = uri"wss://lucuma-odb-development.herokuapp.com/ws"
      sc      <- Resource.eval(ApolloWebSocketClient.of[F, Unit](uri)(Async[F], Logger[F], backend))
      _       <- Resource.make(sc.connect() >> sc.initialize())(_ => sc.terminate() >> sc.disconnect())
    } yield sc

  def run =
    withLogger[IO].use { implicit logger =>
      withStreamingClient[IO].use { implicit client =>
        for {
          result       <- client.request(Query)
          _            <- IO.println(result)
          subscription <- client.subscribe(Subscription)
          fiber        <- subscription.stream.evalMap(IO.println).compile.drain.start
          _            <- IO.sleep(10.seconds)
          _            <- subscription.stop()
          _            <- fiber.join
        } yield ()
      }
    }
}
